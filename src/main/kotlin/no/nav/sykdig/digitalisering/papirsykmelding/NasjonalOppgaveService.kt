package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.AvvisSykmeldingRequest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.FerdigstillRegistrering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.Utfall
import no.nav.sykdig.digitalisering.sykmelding.service.JournalpostService
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.securelog
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val oppgaveClient: OppgaveClient,
    private val journalpostService: JournalpostService,
    private val oppgaveSecurityService: OppgaveSecurityService
) {
    val log = applog()
    val securelog = securelog()
    val mapper = jacksonObjectMapper()

    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        if (eksisterendeOppgave.isPresent) {
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.get().id))
        }
        return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null))
    }

    fun oppdaterOppgave(sykmeldingId: String, utfall: String, ferdigstiltAv: String, avvisningsgrunn: String?): NasjonalManuellOppgaveDAO {
        return nasjonalOppgaveRepository.save(
            mapToUpdateDao(sykmeldingId, utfall, ferdigstiltAv, avvisningsgrunn, nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId).get()),
        )
    }

    fun findByOppgaveId(oppgaveId: Int): NasjonalManuellOppgaveDAO? {
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId)
        if (!oppgave.isPresent) return null
        return oppgave.get()
    }

    suspend fun ferdigstillOppgave(
        ferdigstillRegistrering: FerdigstillRegistrering,
        beskrivelse: String?,
        loggingMeta: LoggingMeta,
        oppgaveId: String,
    ) {
        oppgaveClient.ferdigstillNasjonalOppgave(oppgaveId, ferdigstillRegistrering.sykmeldingId, ferdigstillRegistrering, loggingMeta)
    }

    fun avvisOppgave(
        oppgaveId: Int,
        request: String,
        authorization: String,
        navEnhet: String,
    ): ResponseEntity<NasjonalManuellOppgaveDAO> {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId)
        val avvisningsgrunn = mapper.readValue(request, AvvisSykmeldingRequest::class.java).reason
        if (eksisterendeOppgave.isPresent) {
            journalpostService.ferdigstillAvvistOppgave(oppgaveId, authorization, navEnhet, oppgaveSecurityService.getNavEmail(), avvisningsgrunn)
            val veilederIdent = oppgaveSecurityService.getNavIdent().veilederIdent
            val res = oppdaterOppgave(
                eksisterendeOppgave.get().sykmeldingId,
                utfall = Utfall.AVVIST.toString(),
                ferdigstiltAv = veilederIdent,
                avvisningsgrunn = avvisningsgrunn,
            )

            log.info("Har avvist oppgave med oppgaveId $oppgaveId")
            return ResponseEntity(res, HttpStatus.OK)
        }
        else {
            log.info("fant ikke oppgave som skulle avvises")
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }


    fun mapToUpdateDao(sykmeldingId: String, utfall: String, ferdigstiltAv: String, avvisningsgrunn: String?, existingEntry: NasjonalManuellOppgaveDAO): NasjonalManuellOppgaveDAO {
        return NasjonalManuellOppgaveDAO(
            id = existingEntry.id,
            sykmeldingId = sykmeldingId,
            journalpostId = existingEntry.journalpostId,
            fnr = existingEntry.fnr,
            aktorId = existingEntry.aktorId,
            dokumentInfoId = existingEntry.dokumentInfoId,
            datoOpprettet = existingEntry.datoOpprettet,
            oppgaveId = existingEntry.oppgaveId,
            ferdigstilt = true,
            papirSmRegistrering = existingEntry.papirSmRegistrering,
            utfall = utfall,
            ferdigstiltAv = ferdigstiltAv,
            datoFerdigstilt = LocalDateTime.now(),
            avvisningsgrunn = avvisningsgrunn,
        )
    }


    fun mapToDao(
        papirManuellOppgave: PapirManuellOppgave,
        existingId: UUID?,
    ): NasjonalManuellOppgaveDAO {
        mapper.registerModules(JavaTimeModule())

        val nasjonalManuellOppgaveDAO =
            NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                fnr = papirManuellOppgave.fnr,
                aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet?.toLocalDateTime(),
                oppgaveId = papirManuellOppgave.oppgaveid,
                ferdigstilt = false,
                papirSmRegistrering =
                    PapirSmRegistering(
                        journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                        oppgaveId = papirManuellOppgave.papirSmRegistering.oppgaveId,
                        fnr = papirManuellOppgave.papirSmRegistering.fnr,
                        aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                        dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                        datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet,
                        sykmeldingId = papirManuellOppgave.papirSmRegistering.sykmeldingId,
                        syketilfelleStartDato = papirManuellOppgave.papirSmRegistering.syketilfelleStartDato,
                        arbeidsgiver = papirManuellOppgave.papirSmRegistering.arbeidsgiver,
                        medisinskVurdering = papirManuellOppgave.papirSmRegistering.medisinskVurdering,
                        skjermesForPasient = papirManuellOppgave.papirSmRegistering.skjermesForPasient,
                        perioder = papirManuellOppgave.papirSmRegistering.perioder,
                        prognose = papirManuellOppgave.papirSmRegistering.prognose,
                        utdypendeOpplysninger = papirManuellOppgave.papirSmRegistering.utdypendeOpplysninger,
                        tiltakNAV = papirManuellOppgave.papirSmRegistering.tiltakNAV,
                        tiltakArbeidsplassen = papirManuellOppgave.papirSmRegistering.tiltakArbeidsplassen,
                        andreTiltak = papirManuellOppgave.papirSmRegistering.andreTiltak,
                        meldingTilNAV = papirManuellOppgave.papirSmRegistering.meldingTilNAV,
                        meldingTilArbeidsgiver = papirManuellOppgave.papirSmRegistering.meldingTilArbeidsgiver,
                        kontaktMedPasient = papirManuellOppgave.papirSmRegistering.kontaktMedPasient,
                        behandletTidspunkt = papirManuellOppgave.papirSmRegistering.behandletTidspunkt,
                        behandler = papirManuellOppgave.papirSmRegistering.behandler,
                    ),
                utfall = null,
                ferdigstiltAv = null,
                datoFerdigstilt = null,
                avvisningsgrunn = null,
            )

        if (existingId != null) {
            nasjonalManuellOppgaveDAO.apply {
                id = existingId
            }
        }

        return nasjonalManuellOppgaveDAO
    }
}
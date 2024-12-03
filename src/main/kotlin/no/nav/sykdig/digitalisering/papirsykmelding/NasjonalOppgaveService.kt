package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.mapAvvisningsgrunn
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.AvvisSykmeldingRequest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Document
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.FerdigstillRegistrering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.Utfall
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.securelog
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val oppgaveClient: OppgaveClient,
    private val oppgaveSecurityService: OppgaveSecurityService,
    private val personService: PersonService,
    private val ferdigstillingService: FerdigstillingService,
) {
    val log = applog()
    val securelog = securelog()
    val mapper = jacksonObjectMapper()

    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)

        if (eksisterendeOppgave != null) {
            log.info("Fant eksisterende oppgave med sykmeldingId ${papirManuellOppgave.sykmeldingId} , oppdaterer oppgave med database id ${eksisterendeOppgave.id}")
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.id))
        }
        val res = nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null))
        log.info("Lagret oppgave med sykmeldingId ${res.sykmeldingId} og med database id ${eksisterendeOppgave?.id}")
        return res
    }

    fun oppdaterOppgave(sykmeldingId: String, utfall: String, ferdigstiltAv: String, avvisningsgrunn: String?): NasjonalManuellOppgaveDAO? {
        val updated = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId)?.copy(
            utfall = utfall,
            ferdigstiltAv = ferdigstiltAv,
            avvisningsgrunn = avvisningsgrunn,
            datoFerdigstilt = LocalDateTime.now(),
            ferdigstilt = true,
        )
        when (updated) {
            null -> log.info("Sykmelding $sykmeldingId not found ")
            else -> nasjonalOppgaveRepository.save(updated)
        }
        return updated
    }

    fun findByOppgaveId(oppgaveId: Int): NasjonalManuellOppgaveDAO? {
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId)

        if (oppgave == null) return null
        return oppgave
    }

    fun getNasjonalOppgave(oppgaveId: String): NasjonalManuellOppgaveDAO {
        val oppgave = findByOppgaveId(oppgaveId.toInt())
        if (oppgave == null) {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw NoOppgaveException("Fant ikke oppgave")
        }
        log.info("Hentet oppgave med id $oppgaveId")
        return oppgave
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
    ): ResponseEntity<NasjonalManuellOppgaveDAO>  {
            val eksisterendeOppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId)

            val avvisningsgrunn = mapper.readValue(request, AvvisSykmeldingRequest::class.java).reason
            if (eksisterendeOppgave != null) {
                val veilederIdent = oppgaveSecurityService.getNavIdent().veilederIdent

                ferdigstillNasjonalAvvistOppgave(oppgaveId, authorization, navEnhet, avvisningsgrunn, veilederIdent)
                val res = oppdaterOppgave(
                    eksisterendeOppgave.sykmeldingId,
                    utfall = Utfall.AVVIST.toString(),
                    ferdigstiltAv = veilederIdent,
                    avvisningsgrunn = avvisningsgrunn,
                )

                log.info("Har avvist oppgave med oppgaveId $oppgaveId")
                return ResponseEntity(res, HttpStatus.NO_CONTENT)
            } else {
                log.info("fant ikke oppgave som skulle avvises")
                return ResponseEntity(HttpStatus.NOT_FOUND)
            }
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
            oppgaveId = papirManuellOppgave.oppgaveId,
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

    fun mapFromDao(
        nasjonalManuellOppgaveDAO: NasjonalManuellOppgaveDAO
    ): PapirManuellOppgave {
        val papirSmRegistering = nasjonalManuellOppgaveDAO.papirSmRegistrering

        requireNotNull(nasjonalManuellOppgaveDAO.oppgaveId)
        requireNotNull(nasjonalManuellOppgaveDAO.dokumentInfoId)
        return PapirManuellOppgave(
            sykmeldingId = nasjonalManuellOppgaveDAO.sykmeldingId,
            fnr = nasjonalManuellOppgaveDAO.fnr,
            oppgaveId = nasjonalManuellOppgaveDAO.oppgaveId,
            papirSmRegistering = PapirSmRegistering(
                journalpostId = papirSmRegistering.journalpostId,
                oppgaveId = papirSmRegistering.oppgaveId,
                fnr = papirSmRegistering.fnr,
                aktorId = papirSmRegistering.aktorId,
                dokumentInfoId = papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirSmRegistering.datoOpprettet,
                sykmeldingId = papirSmRegistering.sykmeldingId,
                syketilfelleStartDato = papirSmRegistering.syketilfelleStartDato,
                arbeidsgiver = papirSmRegistering.arbeidsgiver,
                medisinskVurdering = papirSmRegistering.medisinskVurdering,
                skjermesForPasient = papirSmRegistering.skjermesForPasient,
                perioder = papirSmRegistering.perioder,
                prognose = papirSmRegistering.prognose,
                utdypendeOpplysninger = papirSmRegistering.utdypendeOpplysninger,
                tiltakNAV = papirSmRegistering.tiltakNAV,
                tiltakArbeidsplassen = papirSmRegistering.tiltakArbeidsplassen,
                andreTiltak = papirSmRegistering.andreTiltak,
                meldingTilNAV = papirSmRegistering.meldingTilNAV,
                meldingTilArbeidsgiver = papirSmRegistering.meldingTilArbeidsgiver,
                kontaktMedPasient = papirSmRegistering.kontaktMedPasient,
                behandletTidspunkt = papirSmRegistering.behandletTidspunkt,
                behandler = papirSmRegistering.behandler,
            ),
            pdfPapirSykmelding = byteArrayOf(),
            documents = listOf(Document(dokumentInfoId = nasjonalManuellOppgaveDAO.dokumentInfoId, tittel = "papirsykmelding")),
        )
    }


    // kom frå jpservice
@Transactional
fun ferdigstillNasjonalAvvistOppgave(
    oppgaveId: Int,
    authorization: String, // skal dette eigentleg brukes til noke?
    navEnhet: String,
    avvisningsgrunn: String?,
    veilederIdent: String,
) {
    val oppgave = getNasjonalOppgave(oppgaveId.toString())
    if (oppgave.fnr != null) {
        val sykmeldt =
            personService.getPerson(
                id = oppgave.fnr,
                callId = oppgave.sykmeldingId,
            )
        val avvistGrunn = enumValues<Avvisingsgrunn>().find { it.name.equals(avvisningsgrunn, ignoreCase = true) }
        ferdigstillingService.ferdigstillNasjonalAvvistJournalpost(
            enhet = navEnhet,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            avvisningsGrunn = avvistGrunn?.let { mapAvvisningsgrunn(it, null) },
            loggingMeta = getLoggingMeta(oppgave.sykmeldingId, oppgave),
        )

    } else {
        log.error("Fant ikke fnr for oppgave med id $oppgaveId")
    }
}

private fun getLoggingMeta(sykmeldingId: String, oppgave: NasjonalManuellOppgaveDAO): LoggingMeta {
    return LoggingMeta(
        mottakId = sykmeldingId,
        dokumentInfoId = oppgave.dokumentInfoId,
        msgId = sykmeldingId,
        sykmeldingId = sykmeldingId,
        journalpostId = oppgave.journalpostId,
    )
}
}

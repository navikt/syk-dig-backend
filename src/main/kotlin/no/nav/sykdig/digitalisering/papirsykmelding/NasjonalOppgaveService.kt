package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.FerdigstillRegistrering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.securelog
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val oppgaveClient: OppgaveClient,
) {
    val log = applog()
    val securelog = securelog()

    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        if (eksisterendeOppgave.isPresent) {
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.get().id))
        }
        return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null))
    }

    fun oppdaterOppgave(sykmeldingId: String, utfall: String, ferdigstiltAv: String, avvisningsgrunn: String?): NasjonalManuellOppgaveDAO {
        return nasjonalOppgaveRepository.save(
            mapToUpdateDao(sykmeldingId, utfall, ferdigstiltAv, avvisningsgrunn, nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId).get()))
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

    fun mapToUpdateDao(sykmeldingId: String, utfall: String, ferdigstiltAv: String, avvisningsgrunn: String?, existingDao: NasjonalManuellOppgaveDAO): NasjonalManuellOppgaveDAO {
        return NasjonalManuellOppgaveDAO(
            id = existingDao.id,
            sykmeldingId = sykmeldingId,
            journalpostId = existingDao.journalpostId,
            fnr = existingDao.fnr,
            aktorId = existingDao.aktorId,
            dokumentInfoId = existingDao.dokumentInfoId,
            datoOpprettet = existingDao.datoOpprettet,
            oppgaveId = existingDao.oppgaveId,
            ferdigstilt = true,
            papirSmRegistrering = existingDao.papirSmRegistrering,
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
        val mapper = jacksonObjectMapper()
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



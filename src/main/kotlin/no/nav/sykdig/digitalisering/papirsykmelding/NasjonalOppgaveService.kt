package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalSykmeldingRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val nasjonalSykmeldingRepository: NasjonalSykmeldingRepository,
) {
    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val papirmanuellOppgaveDAO = mapToDao(papirManuellOppgave)
        return nasjonalOppgaveRepository.save(papirmanuellOppgaveDAO)
    }

    fun mapToDao(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val mapper = jacksonObjectMapper()
        mapper.registerModules(JavaTimeModule())

        return NasjonalManuellOppgaveDAO(
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
    }

    // steg 2
    fun ferdigstillNasjonalOppgave(
        sykmeldingId: String,
        utfall: String,
        ferdigstiltAv: String,
        avvisningsgrunn: String?,
        ferdigstiltDato: LocalDateTime,
    ) {
        /*val nasjonalOppgave = repository.findBySykmeldingId(sykmeldingId).apply {
            utfall = utfall,
            ferdigstiltAv = ferdigstiltAv,
            avvisningsgrunn = avvisningsgrunn,
            ferdigstiltDato = ferdigstiltDato
        }
        repository.save(nasjonalOppgave)*/
    }
}

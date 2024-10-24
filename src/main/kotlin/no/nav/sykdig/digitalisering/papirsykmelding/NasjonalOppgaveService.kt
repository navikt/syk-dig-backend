package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
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
                mapper.writeValueAsString(
                    papirManuellOppgave.papirSmRegistering.let {
                        it.datoOpprettet?.toLocalDateTime()
                    },
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

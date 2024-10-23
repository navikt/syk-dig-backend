package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalSykmeldingRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val nasjonalSykmeldingRepository: NasjonalSykmeldingRepository,
) {
    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave) {
        nasjonalOppgaveRepository.save(papirManuellOppgave.mapToDao())
    }

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

package no.nav.sykdig.oppgavemottak

import no.nav.sykdig.applog
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.metrics.MetricRegister
import no.nav.sykdig.utils.toOppgaveDbModel
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class MottaOppgaverFraKafka(
    private val oppgaveRepository: OppgaveRepository,
    private val metricRegister: MetricRegister,
) {
    val log = applog()

    fun lagre(
        sykmeldingId: String,
        digitaliseringsoppgave: DigitaliseringsoppgaveKafka,
    ) {
        log.info("Mottatt Kafkaoppgave med id ${digitaliseringsoppgave.oppgaveId} for sykmeldingId $sykmeldingId")
        val opprettet = OffsetDateTime.now(ZoneOffset.UTC)
        oppgaveRepository.lagreOppgave(
            toOppgaveDbModel(digitaliseringsoppgave, opprettet, sykmeldingId),
        )
        metricRegister.mottatOppgave.increment()
    }
}

data class DokumentKafka(
    val tittel: String,
    val dokumentInfoId: String,
)

data class DigitaliseringsoppgaveKafka(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val dokumenter: List<DokumentKafka>,
    val type: String,
    val source: String = "scanning",
)

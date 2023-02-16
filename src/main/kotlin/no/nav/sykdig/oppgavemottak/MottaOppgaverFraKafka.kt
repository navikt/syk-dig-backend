package no.nav.sykdig.oppgavemottak

import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.logger
import no.nav.sykdig.metrics.MetricRegister
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class MottaOppgaverFraKafka(
    private val oppgaveRepository: OppgaveRepository,
    private val metricRegister: MetricRegister
) {
    val log = logger()
    fun lagre(sykmeldingId: String, digitaliseringsoppgave: DigitaliseringsoppgaveKafka) {
        log.info("Mottatt oppgave med id ${digitaliseringsoppgave.oppgaveId} for sykmeldingId $sykmeldingId")
        val opprettet = OffsetDateTime.now(ZoneOffset.UTC)
        oppgaveRepository.lagreOppgave(
            OppgaveDbModel(
                oppgaveId = digitaliseringsoppgave.oppgaveId,
                fnr = digitaliseringsoppgave.fnr,
                journalpostId = digitaliseringsoppgave.journalpostId,
                dokumentInfoId = digitaliseringsoppgave.dokumentInfoId,
                dokumenter = digitaliseringsoppgave.dokumenter?.map {
                    DokumentDbModel(
                        dokumentInfoId = it.dokumentInfoId,
                        tittel = it.tittel
                    )
                },
                opprettet = opprettet,
                ferdigstilt = null,
                tilbakeTilGosys = false,
                sykmeldingId = UUID.fromString(sykmeldingId),
                type = digitaliseringsoppgave.type,
                sykmelding = null,
                endretAv = "syk-dig-backend",
                timestamp = opprettet
            )
        )
        metricRegister.MOTTATT_OPPGAVE.increment()
    }
}

data class DokumentKafka(
    val tittel: String,
    val dokumentInfoId: String
)

data class DigitaliseringsoppgaveKafka(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val dokumenter: List<DokumentKafka>?,
    val type: String
)

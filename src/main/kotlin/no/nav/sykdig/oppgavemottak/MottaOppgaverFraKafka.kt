package no.nav.sykdig.oppgavemottak

import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.logger
import no.nav.sykdig.model.OppgaveDbModel
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class MottaOppgaverFraKafka(val oppgaveRepository: OppgaveRepository) {
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
                opprettet = opprettet,
                ferdigstilt = null,
                sykmeldingId = UUID.fromString(sykmeldingId),
                type = digitaliseringsoppgave.type,
                sykmelding = null,
                endretAv = "syk-dig-backend",
                timestamp = opprettet
            )
        )
    }
}

data class DigitaliseringsoppgaveKafka(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val type: String
)

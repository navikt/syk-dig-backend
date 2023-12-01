package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate
import java.time.OffsetDateTime

data class CreateOppgaveRequest(
    val journalpostId: String,
    val tema: String,
    val oppgavetype: String,
    val prioritet: String,
    val aktivDato: OffsetDateTime,
    val behandlesAvApplikasjon: String,
)

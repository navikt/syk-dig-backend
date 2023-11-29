package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate

data class CreateOppgaveRequest(
    val journalpostId: String,
    val tema: String,
    val oppgavetype: String,
    val prioritet: String,
    val aktivDato: LocalDate,
    val behandlesAvApplikasjon: String,
)

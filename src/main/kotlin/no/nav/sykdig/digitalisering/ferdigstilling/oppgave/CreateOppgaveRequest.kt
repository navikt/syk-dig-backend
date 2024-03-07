package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate

data class CreateOppgaveRequest(
    val aktoerId: String,
    val journalpostId: String,
    val opprettetAvEnhetsnr: String? = null,
    val tema: String,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val prioritet: String,
    val aktivDato: LocalDate,
    val behandlesAvApplikasjon: String,
    val fristFerdigstillelse: LocalDate,
    val tildeltEnhetsnr: String,
)

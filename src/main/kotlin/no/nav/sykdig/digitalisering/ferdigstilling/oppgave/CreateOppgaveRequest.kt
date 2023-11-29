package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate

data class CreateOppgaveRequest(
    val id: Int,
    val tildeltEnhetsnr: String,
    val versjon: Int,
    val tema: String,
    val oppgavetype: OppgaveType,
    val status: Oppgavestatus,
    val prioritet: String,
    val aktivDato: LocalDate,
)

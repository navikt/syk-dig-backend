package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

data class GetOppgaveResponse(
    val versjon: Int,
    val status: Oppgavestatus
)

package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

data class PatchOppgaveRequest(
    val versjon: Int,
    val status: Oppgavestatus,
    val id: Int,
    val mappeId: Int? = null
)

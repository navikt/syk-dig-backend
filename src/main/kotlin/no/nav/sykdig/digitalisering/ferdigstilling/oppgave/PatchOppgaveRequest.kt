package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

data class PatchFerdigStillOppgaveRequest(
    val versjon: Int,
    val status: Oppgavestatus,
    val id: Int,
    val mappeId: Int? = null,
)

data class PatchToGosysOppgaveRequest(
    val versjon: Int,
    val status: Oppgavestatus,
    val id: Int,
    val mappeId: Int? = null,
    val behandlesAvApplikasjon: String?,
    val tilordnetRessurs: String?,
    val beskrivelse: String?,
)

data class OppdaterOppgaveRequest(
    val id: Int,
    val versjon: Int,
    val behandlesAvApplikasjon: String,
)

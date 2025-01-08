package no.nav.sykdig.oppgave.model

data class PatchFerdigStillOppgaveRequest(
    val versjon: Int,
    val status: OppgaveStatus,
    val id: Int,
    val mappeId: Int? = null,
)

data class PatchFerdigstillNasjonalOppgaveRequest(
    val id: Int,
    val versjon: Int,
    val status: OppgaveStatus,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Int?,
    val beskrivelse: String? = null,
)

data class PatchToGosysOppgaveRequest(
    val versjon: Int,
    val status: OppgaveStatus,
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

package no.nav.sykdig.gosys.models

data class PatchFerdigStillOppgaveRequest(
    val versjon: Int,
    val status: OppgaveStatus,
    val id: Int,
    val mappeId: Int? = null,
    val endretAvEnhetsnr: String?
)

data class PatchFerdigstillNasjonalOppgaveRequest(
    val id: Int,
    val versjon: Int,
    val status: OppgaveStatus,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Int?,
    val beskrivelse: String? = null,
    val endretAvEnhetsnr: String?
)

data class PatchToGosysOppgaveRequest(
    val versjon: Int,
    val status: OppgaveStatus,
    val id: Int,
    val mappeId: Int? = null,
    val behandlesAvApplikasjon: String?,
    val tilordnetRessurs: String?,
    val beskrivelse: String?,
    val endretAvEnhetsnr: String?
)

data class OppdaterOppgaveRequest(
    val id: Int,
    val versjon: Int,
    val behandlesAvApplikasjon: String,
)

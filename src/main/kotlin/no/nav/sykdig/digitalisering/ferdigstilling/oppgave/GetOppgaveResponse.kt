package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

data class GetOppgaveResponse(
    val versjon: Int,
    val status: Oppgavestatus,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,
    val mappeId: Int? = null,
    val beskrivelse: String? = null,
)

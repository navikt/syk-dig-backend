package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate

data class GetOppgaveResponse(
    val versjon: Int,
    val status: Oppgavestatus,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,
    val mappeId: Int? = null,
    val beskrivelse: String? = null,
    val oppgaveType: OppgaveType,
    val aktivDato: LocalDate? = null,
    val prioritet: String? = null,
    val id: Int? = null,
    val tema: String? = null,
    val tildeltEnhetsnr: String,
)

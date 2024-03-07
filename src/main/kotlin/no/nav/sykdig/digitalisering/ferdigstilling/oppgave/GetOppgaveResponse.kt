package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate

data class GetOppgaveResponse(
    val versjon: Int,
    val status: Oppgavestatus,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,
    val mappeId: Int? = null,
    val beskrivelse: String? = null,
    val oppgavetype: OppgaveType,
    val aktivDato: LocalDate? = null,
    val prioritet: String? = null,
    val id: Int? = null,
    val tema: String? = null,
    val tildeltEnhetsnr: String,
    val duplikat: Boolean?,
)

data class TempOppgaveResponse(
    val versjon: Int,
    val aktoerId: String,
    val status: Oppgavestatus,
    val aktivDato: LocalDate? = null,
    val id: Int? = null,
    val tema: String? = null,
    val tildeltEnhetsnr: String,
    val oppgavetype: OppgaveType,
)

data class OppgaveResponse(
    val oppgaver: List<TempOppgaveResponse>,
)

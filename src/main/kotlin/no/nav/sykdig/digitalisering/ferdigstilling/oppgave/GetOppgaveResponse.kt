package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate

data class GetOppgaveResponse(
    val versjon: Int,
    val status: Oppgavestatus,
    val id: Int? = null,
    val oppgavetype: String,
    val journalpostId: String?,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,
    val mappeId: Int? = null,
    val beskrivelse: String? = null,
    val aktivDato: LocalDate? = null,
    val prioritet: String? = null,
    val tema: String? = null,
    val tildeltEnhetsnr: String,
    val ferdigstiltTidspunkt: String?,
    val duplikat: Boolean?,
    val metadata: Map<String, String?>?,
    val behandlingstype: String?,
    val behandlingstema: String?,
)

data class AllOppgaveResponse(
    val versjon: Int,
    val aktoerId: String,
    val status: Oppgavestatus,
    val aktivDato: LocalDate? = null,
    val id: Int? = null,
    val tema: String? = null,
    val tildeltEnhetsnr: String,
    val oppgavetype: AllOppgaveType,
)

data class AllOppgaveResponses(
    val oppgaver: List<AllOppgaveResponse>,
)

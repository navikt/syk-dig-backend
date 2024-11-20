package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import java.time.LocalDate

data class GetOppgaveResponse(
    val versjon: Int,
    val status: OppgaveStatus,
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

data class NasjonalOppgaveResponse(
    val id: Int? = null,
    val versjon: Int? = null,
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String,
    val status: String? = null,
    val mappeId: Int? = null,
)

data class NasjonalFerdigstillOppgave(
    val id: Int,
    val versjon: Int,
    val status: OppgaveStatus,
    val tilordnetRessurs: String,
    val tildeltEnhetsnr: String,
    val mappeId: Int?,
    val beskrivelse: String? = null,
)

data class AllOppgaveResponse(
    val versjon: Int,
    val aktoerId: String,
    val status: OppgaveStatus,
    val aktivDato: LocalDate? = null,
    val id: Int? = null,
    val tema: String? = null,
    val tildeltEnhetsnr: String,
    val oppgavetype: AllOppgaveType,
)

data class AllOppgaveResponses(
    val oppgaver: List<AllOppgaveResponse>,
)

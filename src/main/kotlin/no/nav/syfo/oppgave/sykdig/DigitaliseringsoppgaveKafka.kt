package no.nav.syfo.oppgave.sykdig

import no.nav.syfo.oppgave.saf.model.DokumentMedTittel

data class DigitaliseringsoppgaveKafka(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String,
    val type: String,
    val dokumenter: List<DokumentMedTittel>,
    val source: String,
)

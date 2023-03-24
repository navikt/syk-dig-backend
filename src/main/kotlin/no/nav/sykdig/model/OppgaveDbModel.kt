package no.nav.sykdig.model

import java.time.OffsetDateTime
import java.util.UUID

data class OppgaveDbModel(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val dokumenter: List<DokumentDbModel>?,
    val opprettet: OffsetDateTime,
    val ferdigstilt: OffsetDateTime?,
    val tilbakeTilGosys: Boolean,
    val avvisingsgrunn: String?,
    val sykmeldingId: UUID,
    val type: String,
    val sykmelding: SykmeldingUnderArbeid?,
    val endretAv: String,
    val timestamp: OffsetDateTime,
    val source: String,
)

data class DokumentDbModel(
    val dokumentInfoId: String,
    val tittel: String,
)

package no.nav.sykdig.model

import java.time.OffsetDateTime
import java.util.UUID

data class Digitaliseringsoppgave(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val opprettet: OffsetDateTime,
    val ferdigstilt: OffsetDateTime?,
    val sykmeldingId: UUID,
    val type: String,
    val sykmelding: SykmeldingUnderArbeid?,
    val endretAv: String,
    val timestamp: OffsetDateTime
)

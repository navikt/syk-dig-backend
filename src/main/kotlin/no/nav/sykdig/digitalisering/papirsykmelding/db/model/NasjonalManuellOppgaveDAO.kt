package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import java.time.LocalDateTime
import java.time.OffsetDateTime

data class NasjonalManuellOppgaveDAO(
    val sykmeldingId: String,
    val journalpostId: String,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: OffsetDateTime?,
    val oppgaveId: Int?,
    val ferdigstilt: Boolean,
    val papirSmRegistrering: PapirSmRegistering,
    var utfall: String?,
    var ferdigstiltAv: String?,
    var datoFerdigstilt: LocalDateTime?,
    var avvisningsgrunn: String?,
)

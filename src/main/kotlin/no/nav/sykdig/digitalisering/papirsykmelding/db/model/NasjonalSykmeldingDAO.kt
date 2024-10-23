package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelding
import java.time.LocalDateTime
import java.time.OffsetDateTime

data class NasjonalSykmeldingDAO(
    val sykmeldingId: String,
    val sykmelding: Sykmelding,
    val timestamp: OffsetDateTime,
    val ferdigstiltAv: String?,
    val datoFerdigstilt: LocalDateTime?,
)

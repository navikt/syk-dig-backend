package no.nav.sykdig.nasjonal.db.models

import jakarta.persistence.Column
import no.nav.sykdig.shared.ReceivedSykmelding
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "nasjonal_sykmelding")
data class NasjonalSykmeldingDAO(
    @Id
    val id: UUID? = null,
    @Column(name = "sykmelding_id", nullable = false)
    val sykmeldingId: String,
    @Column(name = "sykmelding", columnDefinition = "jsonb", nullable = false)
    val sykmelding: ReceivedSykmelding,
    @Column(name = "timestamp")
    val timestamp: OffsetDateTime,
    @Column(name = "ferdigstilt_av")
    val ferdigstiltAv: String?,
    @Column(name = "dato_ferdigstilt")
    val datoFerdigstilt: OffsetDateTime?
)

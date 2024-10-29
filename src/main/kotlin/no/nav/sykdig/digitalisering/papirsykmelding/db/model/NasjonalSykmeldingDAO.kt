package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import jakarta.persistence.GeneratedValue
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelding
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "nasjonal_sykmelding")
data class NasjonalSykmeldingDAO(
    @Id
    @GeneratedValue(generator = "UUID")
    var id: UUID? = null,
    @Column("sykmelding_id")
    val sykmeldingId: String,
    @Column("sykmelding")
    val sykmelding: Sykmelding,
    @Column("timestamp")
    val timestamp: OffsetDateTime,
    @Column("dato_ferdigstilt")
    val datoFerdigstilt: LocalDateTime? = null,
    @Column("ferdigstilt_av")
    val ferdigstiltAv: String? = null,
)

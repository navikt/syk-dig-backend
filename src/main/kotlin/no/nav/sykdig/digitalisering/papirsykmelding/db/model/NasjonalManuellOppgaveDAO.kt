package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "nasjonal_manuelloppgave")
open class NasjonalManuellOppgaveDAO(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    var id: UUID? = null,

    @Column(name = "sykmelding_id", nullable = false)
    val sykmeldingId: String,

    @Column(name = "journalpost_id", nullable = false)
    val journalpostId: String,

    @Column(name = "fnr")
    val fnr: String?,

    @Column(name = "aktor_id")
    val aktorId: String?,

    @Column(name = "dokument_info_id")
    val dokumentInfoId: String?,

    @Column(name = "dato_opprettet")
    val datoOpprettet: LocalDateTime?,

    @Column(name = "oppgave_id")
    val oppgaveId: Int?,

    @Column(name = "ferdigstilt", nullable = false)
    val ferdigstilt: Boolean = false,

    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = JsonConverter::class)
    @Column(name = "papir_sm_registrering", columnDefinition = "jsonb")
    val papirSmRegistrering: String,

    @Column(name = "utfall")
    var utfall: String?,

    @Column(name = "ferdigstilt_av")
    var ferdigstiltAv: String?,

    @Column(name = "dato_ferdigstilt")
    var datoFerdigstilt: LocalDateTime?,

    @Column(name = "avvisningsgrunn")
    var avvisningsgrunn: String?
)


@Converter
class JsonConverter : AttributeConverter<PapirSmRegistering, String> {
    private val objectMapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: PapirSmRegistering?): String? {
        return attribute?.let { objectMapper.writeValueAsString(it) }
    }

    override fun convertToEntityAttribute(dbData: String?): PapirSmRegistering? {
        return dbData?.let { objectMapper.readValue(it) }
    }
}

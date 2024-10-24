package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime


@Entity
@Table(name = "nasjonal_manuelloppgave")
@TypeDef(name = "jsonb", typeClass = JsonBinaryType::class) // finn rett versjon for denne
data class NasjonalManuellOppgaveDAO(
    @Id
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
    val ferdigstilt: Boolean,
    @Type(type = "jsonb") // finn rett versjon for denne
    @Column(name = "papir_sm_registrering", columnDefinition = "jsonb")
    val papirSmRegistrering: String,
    @Column(name = "utfall")
    var utfall: String?,
    @Column(name = "ferdigstilt_av")
    var ferdigstiltAv: String?,
    @Column(name = "dato_ferdigstilt")
    var datoFerdigstilt: LocalDateTime?,
    @Column(name = "avvisningsgrunn")
    var avvisningsgrunn: String?,
)

@Converter
class JsonConverter : AttributeConverter<Any, String> {
    private val objectMapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: Any?): String {
        return attribute?.let { objectMapper.writeValueAsString(it) } ?: "{}"
    }

    override fun convertToEntityAttribute(dbData: String?): Any {
        return dbData?.let { objectMapper.readValue(it) } ?: emptyMap<String, Any>()
    }
}

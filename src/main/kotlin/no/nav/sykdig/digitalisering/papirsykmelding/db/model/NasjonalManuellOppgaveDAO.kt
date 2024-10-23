package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Entity
@Table(name = "nasjonal_manuelloppgave")
//@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
data class NasjonalManuellOppgaveDAO(
    @Id
    val sykmeldingId: String,
    val journalpostId: String,
    val fnr: String?,
    val aktorId: String?,
    val dokumentInfoId: String?,
    val datoOpprettet: LocalDateTime?,
    val oppgaveId: Int?,
    val ferdigstilt: Boolean,
//    @JdbcTypeCode(SqlTypes.JSON)
    @Type(type = "jsonb")
    @Column(name = "papir_sm_registrering")
    val papirSmRegistrering: String,
    var utfall: String?,
    var ferdigstiltAv: String?,
    var datoFerdigstilt: LocalDateTime?,
    var avvisningsgrunn: String?,
)

package no.nav.sykdig.digitalisering.papirsykmelding.db.model

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.annotation.Id
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

@Table(name = "nasjonal_manuelloppgave")
open class NasjonalManuellOppgaveDAO(
    @Id
    @Column("sykmelding_id")
    val sykmeldingId: String,
    @Column("journalpost_id")
    val journalpostId: String,
    @Column("fnr")
    val fnr: String? = null,
    @Column("aktor_id")
    val aktorId: String? = null,
    @Column("dokument_info_id")
    val dokumentInfoId: String? = null,
    @Column("dato_opprettet")
    val datoOpprettet: LocalDateTime? = null,
    @Column("oppgave_id")
    val oppgaveId: Int? = null,
    @Column("ferdigstilt")
    val ferdigstilt: Boolean = false,
    @Column("papir_sm_registrering")
    val papirSmRegistrering: PapirSmRegistering,
    @Column("utfall")
    var utfall: String? = null,
    @Column("ferdigstilt_av")
    var ferdigstiltAv: String? = null,
    @Column("dato_ferdigstilt")
    var datoFerdigstilt: LocalDateTime? = null,
    @Column("avvisningsgrunn")
    var avvisningsgrunn: String? = null,
)

@WritingConverter
class PapirSmRegistreringWritingConverter : Converter<PapirSmRegistering, PGobject> {
    private val objectMapper = jacksonObjectMapper()

    override fun convert(source: PapirSmRegistering): PGobject {
        val jsonObject = PGobject()
        jsonObject.type = "jsonb"
        objectMapper.registerModule(JavaTimeModule())
        jsonObject.value = objectMapper.writeValueAsString(source)
        return jsonObject
    }
}

@ReadingConverter
class PapirSmRegistreringReadingConverter : Converter<PGobject, PapirSmRegistering> {
    private val objectMapper = jacksonObjectMapper()

    override fun convert(source: PGobject): PapirSmRegistering {
        objectMapper.registerModule(JavaTimeModule())
        return objectMapper.readValue(source.value!!, PapirSmRegistering::class.java) // bedre håndtering enn !!
    }
}

@Configuration
class JdbcConfiguration {
    @Bean
    fun jdbcCustomConversions(): JdbcCustomConversions {
        return JdbcCustomConversions(
            listOf(
                PapirSmRegistreringWritingConverter(),
                PapirSmRegistreringReadingConverter(),
            ),
        )
    }
}

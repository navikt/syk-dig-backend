package no.nav.sykdig.nasjonal.db.models

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.shared.*
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import tools.jackson.module.kotlin.jacksonMapperBuilder

@ReadingConverter
class OffsetDateTimeReadingConverter : Converter<Any, OffsetDateTime> {
    override fun convert(source: Any): OffsetDateTime {
        return when (source) {
            is Timestamp -> source.toInstant().atOffset(ZoneOffset.UTC)
            is OffsetDateTime -> source
            else -> throw IllegalArgumentException("Unexpected source type: ${source::class}")
        }
    }
}

@WritingConverter
class PapirSmRegistreringWritingConverter : Converter<PapirSmRegistering, PGobject> {
    override fun convert(source: PapirSmRegistering): PGobject {
        val jsonObject = PGobject()
        jsonObject.type = "jsonb"
        jsonObject.value = jsonMapper.writeValueAsString(source)
        return jsonObject
    }
}

@ReadingConverter
class PapirSmRegistreringReadingConverter : Converter<PGobject, PapirSmRegistering> {
    private val objectMapper = jacksonMapperBuilder().build()

    override fun convert(source: PGobject): PapirSmRegistering {
        return objectMapper.readValue(
            source.value!!,
            PapirSmRegistering::class.java,
        ) // bedre håndtering enn !!
    }
}

@WritingConverter
class SykmeldingWritingConverter : Converter<Sykmelding, PGobject> {
    override fun convert(source: Sykmelding): PGobject {
        val jsonObject = PGobject()
        jsonObject.type = "jsonb"
        jsonObject.value = jsonMapper.writeValueAsString(source)
        return jsonObject
    }
}

@ReadingConverter
class SykmeldingReadingConverter : Converter<PGobject, Sykmelding> {
    private val objectMapper = jacksonMapperBuilder().build()

    override fun convert(source: PGobject): Sykmelding {
        return objectMapper.readValue(
            source.value!!,
            Sykmelding::class.java,
        ) // bedre håndtering enn !!
    }
}

@Configuration
class JdbcConfiguration {
    @Bean
    fun jdbcCustomConversions(): JdbcCustomConversions {
        return JdbcCustomConversions(
            listOf(
                OffsetDateTimeReadingConverter(),
                PapirSmRegistreringWritingConverter(),
                PapirSmRegistreringReadingConverter(),
                SykmeldingWritingConverter(),
                SykmeldingReadingConverter(),
            )
        )
    }
}

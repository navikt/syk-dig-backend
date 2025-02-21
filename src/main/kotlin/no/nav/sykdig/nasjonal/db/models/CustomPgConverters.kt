package no.nav.sykdig.nasjonal.db.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.shared.*
import org.postgresql.util.PGobject
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.log


@ReadingConverter
class OffsetDateTimeReadingConverter : Converter<Any, OffsetDateTime> {
    override fun convert(source: Any): OffsetDateTime {
        return when (source) {
            is Timestamp -> source.toInstant().atOffset(ZoneOffset.UTC)
            is OffsetDateTime -> source // If already OffsetDateTime, return as-is
            else -> throw IllegalArgumentException("Unexpected source type: ${source::class}")
        }
    }
}

@WritingConverter
class PapirSmRegistreringWritingConverter : Converter<PapirSmRegistering, PGobject> {
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

@WritingConverter
class SykmeldingWritingConverter : Converter<Sykmelding, PGobject> {
    override fun convert(source: Sykmelding): PGobject {
        val jsonObject = PGobject()
        jsonObject.type = "jsonb"
        objectMapper.registerModule(JavaTimeModule())
        jsonObject.value = objectMapper.writeValueAsString(source)
        return jsonObject
    }
}

@ReadingConverter
class SykmeldingReadingConverter : Converter<PGobject, Sykmelding> {
    private val objectMapper = jacksonObjectMapper()

    override fun convert(source: PGobject): Sykmelding {
        objectMapper.registerModule(JavaTimeModule())
        return objectMapper.readValue(source.value!!, Sykmelding::class.java) // bedre håndtering enn !!
    }
}

@WritingConverter
class ReceivedSykmeldingToJsonConverter(private val objectMapper: ObjectMapper) : Converter<ReceivedSykmelding, PGobject> {
    override fun convert(source: ReceivedSykmelding): PGobject {
        val json = objectMapper.writeValueAsString(source)
        return PGobject().apply {
            type = "jsonb"
            value = json
        }
    }
}

@ReadingConverter
class JsonToReceivedSykmeldingConverter(private val objectMapper: ObjectMapper) : Converter<PGobject, ReceivedSykmelding> {
    val log = applog()
    override fun convert(source: PGobject): ReceivedSykmelding {
        return try {
            objectMapper.readValue(source.value!!, ReceivedSykmelding::class.java)
        } catch (e: Exception) {
            val gammelSykmelding = objectMapper.readValue(source.value!!, Sykmelding::class.java)
            log.info("Migrerer gammel Sykmelding til ReceivedSykmelding: ${gammelSykmelding.id}")

            konverterTilReceivedSykmelding(gammelSykmelding)
        }
    }

    private fun konverterTilReceivedSykmelding(sykmelding: Sykmelding): ReceivedSykmelding {
        return ReceivedSykmelding(
            sykmelding = sykmelding,
            personNrPasient = "UKJENT",
            personNrLege = "UKJENT",
            legeHprNr = sykmelding.behandler.hpr ?: "UKJENT",
            msgId = sykmelding.id,
            navLogId = sykmelding.id,
            mottattDato = LocalDateTime.now(),
            tlfPasient = "UKJENT",
            legeHelsepersonellkategori = "UKJENT",
            legekontorOrgNr = "UKJENT",
            legekontorHerId = "UKJENT",
            legekontorReshId = "UKJENT",
            legekontorOrgName = "UKJENT",
            rulesetVersion = "UKJENT",
            merknader = null,
            partnerreferanse = "UKJENT",
            vedlegg = null,
            utenlandskSykmelding = null,
            fellesformat = "UKJENT",
            tssid = "UKJENT",
            validationResult = null,
        )
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
                ReceivedSykmeldingToJsonConverter(objectMapper),
                JsonToReceivedSykmeldingConverter(objectMapper)
            ),
        )
    }

}


package no.nav.sykdig.shared

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class LocalDateTimeToOffsetDateTimeDeserializer : JsonDeserializer<OffsetDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OffsetDateTime {
        val localDateTime = LocalDateTime.parse(p.valueAsString) // Konverter fra String til LocalDateTime
        return localDateTime.atOffset(ZoneOffset.UTC) // Legger til UTC offset
    }
}

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

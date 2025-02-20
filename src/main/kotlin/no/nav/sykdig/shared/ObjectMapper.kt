package no.nav.sykdig.shared

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OffsetDateTimeDeserializer : JsonDeserializer<OffsetDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OffsetDateTime {
        val text = p.text
        return if (text.contains("Z") || text.contains("+") || text.contains("-")) {
            OffsetDateTime.parse(text)
        } else {
            LocalDateTime.parse(text).atOffset(ZoneOffset.UTC)
        }
    }
}

val objectMapper: ObjectMapper =
    ObjectMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
        registerModule(
                SimpleModule().apply {
                    addDeserializer(OffsetDateTime::class.java, OffsetDateTimeDeserializer())
                }
            )
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

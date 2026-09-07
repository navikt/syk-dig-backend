package no.nav.sykdig.shared

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

class FlexibleOffsetDateTimeDeserializer : JsonDeserializer<OffsetDateTime>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OffsetDateTime {
        val text = p.valueAsString

        return try {
            OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } catch (e: Exception) {
            val localDateTime = LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            localDateTime.atOffset(ZoneOffset.UTC)
        }
    }
}

val jsonMapper: JsonMapper =
    jacksonMapperBuilder().defaultTimeZone(TimeZone.getTimeZone("UTC")).build()

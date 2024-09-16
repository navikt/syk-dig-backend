package no.nav.sykdig.oppgavemottak
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.time.LocalDateTime

class LocalDateTimeArrayDeserializer : JsonDeserializer<LocalDateTime>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): LocalDateTime {
        val node = parser.codec.readTree<com.fasterxml.jackson.databind.node.ArrayNode>(parser)
        val year = node.get(0).asInt()
        val month = node.get(1).asInt()
        val day = node.get(2).asInt()
        val hour = node.get(3).asInt()
        val minute = node.get(4).asInt()
        val second = node.get(5).asInt()
        val nanoOfSecond = node.get(6).asInt()

        return LocalDateTime.of(year, month, day, hour, minute, second, nanoOfSecond)
    }
}
package no.nav.sykdig.shared



import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.postgresql.util.PGobject

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
    override fun convert(source: PGobject): ReceivedSykmelding {
        return objectMapper.readValue(source.value!!)
    }
}
package no.nav.sykdig.shared.utils

import org.apache.kafka.common.serialization.Serializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

class JacksonKafkaSerializer : Serializer<Any> {
    private val objectMapper: JsonMapper = jacksonMapperBuilder().build()

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        objectMapper
    }

    override fun serialize(topic: String?, data: Any?): ByteArray =
        objectMapper.writeValueAsBytes(data)

    override fun close() {}

    companion object {
        const val SERIALIZE_AS_TIMESTAMP = "no.nav.serialize.as.timestamp"
    }
}

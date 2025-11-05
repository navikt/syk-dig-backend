package no.nav.sykdig.shared.kafka

import no.nav.sykdig.shared.ReceivedSykmelding
import no.nav.sykdig.shared.config.kafka.OK_SYKMELDING_TOPIC
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SykmeldingProducer(
    private val kafkaProducer: KafkaProducer<String, ReceivedSykmelding>,
    @param:Value("\${nais.appname}") private val appName: String,
    @param:Value("\${nais.namespace}") private val namespace: String,
) {
    private final val SOURCE_NAMESPACE = "source-namespace"
    private final val SOURCE_APP = "source-app"

    private final val log = org.slf4j.LoggerFactory.getLogger(SykmeldingProducer::class.java)

    fun send(receivedSykmelding: ReceivedSykmelding) {
        val record =
            ProducerRecord(
                OK_SYKMELDING_TOPIC,
                receivedSykmelding.sykmelding.id,
                receivedSykmelding,
            )
        record.headers().add(SOURCE_NAMESPACE, namespace.toByteArray())
        record.headers().add(SOURCE_APP, appName.toByteArray())
        kafkaProducer.send(record).get()
        log.info(
            "Sykmelding sendt to kafka topic $OK_SYKMELDING_TOPIC sykmelding id ${receivedSykmelding.sykmelding.id}"
        )
    }
}

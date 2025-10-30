package no.nav.sykdig.nasjonal.kafka

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.shared.ReceivedSykmelding
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.config.kafka.OK_SYKMELDING_TOPIC
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.shared.utils.PROCESSING_TARGET_HEADER
import no.nav.sykdig.shared.utils.TSM_PROCESSING_TARGET_VALUE
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Service

@Service
class NasjonalKafkaService(
    private val sykmeldingOKProducer: KafkaProducer<String, ReceivedSykmelding>
) {
    val log = applog()
    val securelog = securelog()
    val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    fun sendSykmeldingToKafka(receivedSykmelding: ReceivedSykmelding) {
        try {
            val record =
                ProducerRecord(
                    OK_SYKMELDING_TOPIC,
                    receivedSykmelding.sykmelding.id,
                    receivedSykmelding,
                )
            record
                .headers()
                .add(PROCESSING_TARGET_HEADER, TSM_PROCESSING_TARGET_VALUE.toByteArray())
            sykmeldingOKProducer.send(record).get()
            log.info(
                "Sykmelding sendt to kafka topic {} sykmelding id {}",
                OK_SYKMELDING_TOPIC,
                receivedSykmelding.sykmelding.id,
            )
        } catch (exception: Exception) {
            log.error(
                "failed to send sykmelding to kafka result for sykmeldingId: {}",
                receivedSykmelding.sykmelding.id,
            )
            throw exception
        }
    }
}

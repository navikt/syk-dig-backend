package no.nav.sykdig.nasjonal.kafka

import no.nav.sykdig.shared.ReceivedSykmelding
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.config.kafka.OK_SYKMELDING_TOPIC
import no.nav.sykdig.shared.kafka.SykmeldingProducer
import no.nav.sykdig.shared.securelog
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonMapperBuilder

@Service
class NasjonalKafkaService(private val sykmeldingOKProducer: SykmeldingProducer) {
    val log = applog()
    val securelog = securelog()
    val objectMapper = jacksonMapperBuilder().build()

    fun sendSykmeldingToKafka(receivedSykmelding: ReceivedSykmelding) {
        try {

            sykmeldingOKProducer.send(receivedSykmelding)
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

package no.nav.sykdig.digitalisering.sykmelding.service

import no.nav.sykdig.digitalisering.sykmelding.CreateSykmeldingKafkaMessage
import no.nav.sykdig.digitalisering.sykmelding.JournalpostMetadata
import no.nav.sykdig.digitalisering.sykmelding.Metadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class SykmeldingService(
    private val sykmeldingKafkaProducer: KafkaProducer<String, CreateSykmeldingKafkaMessage>,
    @Qualifier("sykmeldingTopic") private val sykmeldingTopic: String,
) {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }

    fun createSykmelding(journalpostId: String, tema: String) {
        try {
            val createSykmeldingKafkaMessage = CreateSykmeldingKafkaMessage(
                metadata = Metadata(),
                data = JournalpostMetadata(
                    journalpostId,
                    tema,
                ),
            )

            sykmeldingKafkaProducer.send(
                ProducerRecord(
                    sykmeldingTopic,
                    journalpostId,
                    createSykmeldingKafkaMessage,
                ),
            ).get()
            log.info(
                "Sykmelding sendt to kafka topic $sykmeldingTopic journalpost id $journalpostId",
            )
        } catch (exception: Exception) {
            log.error("Failed to produce create sykmelding to topic", exception)
            throw exception
        }
    }
}
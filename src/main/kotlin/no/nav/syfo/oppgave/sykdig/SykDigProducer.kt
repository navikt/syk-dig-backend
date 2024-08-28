package no.nav.syfo.oppgave.sykdig

import no.nav.syfo.logger
import no.nav.syfo.objectMapper
import no.nav.syfo.securelog
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SykDigProducer(
    private val kafkaProducer: KafkaProducer<String, DigitaliseringsoppgaveKafka>,
    private val topicName: String,
) {
    fun send(
        sporingsId: String,
        digitaliseringsoppgave: DigitaliseringsoppgaveKafka,
    ) {
        try {
            kafkaProducer
                .send(
                    ProducerRecord(
                        topicName,
                        sporingsId,
                        digitaliseringsoppgave,
                    ),
                )
                .get()
        } catch (ex: Exception) {
            securelog.error(
                "Noe gikk galt ved skriving av digitaliseringsoppgave til kafka for oppgave ${objectMapper.writeValueAsString(
                    digitaliseringsoppgave,
                )} med sporingsId $sporingsId",
                ex.message,
            )
            logger.error(
                "Noe gikk galt ved skriving av digitaliseringsoppgave til kafka for oppgaveId ${digitaliseringsoppgave.oppgaveId} og sporingsId $sporingsId",
                ex.message,
            )
            throw ex
        }
    }
}

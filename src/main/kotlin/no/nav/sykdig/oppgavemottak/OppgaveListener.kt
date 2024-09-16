package no.nav.sykdig.oppgavemottak

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class OppgaveListener(
    val mottaOppgaverFraKafka: MottaOppgaverFraKafka,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    @KafkaListener(
        topics = ["\${oppgave.topic}"],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.info("Reading message from Kafka topic " + cr.value())
            val oppgaveRecord: OppgaveKafkaAivenRecord = objectMapper.readValue(cr.value())
            // usikker på om vi må nullsjekke her(?)

            val isOppgaveOpprettet = oppgaveRecord.hendelse.hendelsestype == Hendelsestype.OPPGAVE_OPPRETTET
            val isValidTema = oppgaveRecord.oppgave.kategorisering.tema in listOf("SYM", "SYK")
            val isCorrectBehandlingstype = oppgaveRecord.oppgave.kategorisering.behandlingstype == "ae0106"
            val isCorrectOppgavetype = oppgaveRecord.oppgave.kategorisering.oppgavetype == "JFR"
            val hasValidBruker = oppgaveRecord.oppgave.bruker != null && oppgaveRecord.oppgave.bruker.identType == IdentType.FOLKEREGISTERIDENT

            val isValidOppgave = isOppgaveOpprettet && isValidTema && isCorrectBehandlingstype && isCorrectOppgavetype && hasValidBruker

            if (isValidOppgave) {
                mottaOppgaverFraKafka.behandleOppgave(oppgaveRecord)
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.info("Error deserializing OppgaveKafkaAivenRecord")
        }
    }
}

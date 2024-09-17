package no.nav.sykdig.oppgavemottak

import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.servlet.http.HttpServletRequest
import no.nav.sykdig.applog
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
    val logger = applog()

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
            logger.info("Feil i å opprette oppgave " + e.message)
        }
    }

    fun extractJwtToken(request: HttpServletRequest): String? {
        val authorizationHeader = request.getHeader("Authorization")
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7)
        }
        return null
    }
}

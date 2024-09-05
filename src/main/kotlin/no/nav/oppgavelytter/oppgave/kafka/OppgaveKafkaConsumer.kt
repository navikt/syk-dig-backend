import no.nav.oppgavelytter.oppgave.OppgaveService
import no.nav.oppgavelytter.oppgave.kafka.Hendelsestype
import no.nav.oppgavelytter.oppgave.kafka.IdentType
import no.nav.oppgavelytter.oppgave.kafka.OppgaveKafkaAivenRecord
import no.nav.sykdig.applog
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class OppgaveKafkaConsumer(
    private val kafkaConsumer: KafkaConsumer<String, OppgaveKafkaAivenRecord>,
    private val oppgaveService: OppgaveService,
) {
    val logger = applog()

    @KafkaListener(topics = ["\${oppgave.topic.name}"], groupId = "syk-dig-backend-consumer")
    fun startConsumer(message: String) {
        while (true) {
            try {
                logger.info("Consumed message: $message")
                consumeMessage()
                break
            } catch (ex: Exception) {
                logger.error("Error processing message", ex)
                try {
                    Thread.sleep(10000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    logger.error("Interrupted while waiting to retry", e)
                }
            }
        }
    }

    private fun consumeMessage() {
        val records = kafkaConsumer.poll(Duration.ofSeconds(1)).mapNotNull { it.value() }
        if (records.isNotEmpty()) {
            records
                .filter {
                    it.hendelse.hendelsestype == Hendelsestype.OPPGAVE_OPPRETTET &&
                        (
                            it.oppgave.kategorisering.tema == "SYM" ||
                                it.oppgave.kategorisering.tema == "SYK"
                        ) &&
                        it.oppgave.kategorisering.behandlingstype == "ae0106" &&
                        it.oppgave.kategorisering.oppgavetype == "JFR" &&
                        it.oppgave.bruker != null &&
                        it.oppgave.bruker.identType == IdentType.FOLKEREGISTERIDENT
                }
                .forEach { oppgaveKafkaAivenRecord ->
                    oppgaveService.handleOppgave(
                        oppgaveKafkaAivenRecord.oppgave.oppgaveId,
                        oppgaveKafkaAivenRecord.oppgave.bruker!!.ident,
                    )
                }
        }
    }
}

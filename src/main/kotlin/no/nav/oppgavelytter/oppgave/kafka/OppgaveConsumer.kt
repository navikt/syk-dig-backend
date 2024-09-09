package no.nav.oppgavelytter.oppgave.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.oppgavelytter.oppgave.OppgaveService
import no.nav.sykdig.applog
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class OppgaveConsumer(
    private val oppgaveService: OppgaveService,
    private val applicationState: no.nav.sykdig.ApplicationState,
) {
    private val logger = applog()

    @KafkaListener(topics = ["teamsykmelding.syk-dig-oppgave"], groupId = "syk-dig-backend-consumer")
    fun consume(oppgaveKafkaAivenRecord: OppgaveKafkaAivenRecord) {
        logger.info("Consuming from topic \${oppgave.topic.name}")
        if (applicationState.ready) {
            try {
                val records =
                    listOf(oppgaveKafkaAivenRecord).filter {
                        it.hendelse.hendelsestype == Hendelsestype.OPPGAVE_OPPRETTET &&
                            (it.oppgave.kategorisering.tema == "SYM" || it.oppgave.kategorisering.tema == "SYK") &&
                            it.oppgave.kategorisering.behandlingstype == "ae0106" &&
                            it.oppgave.kategorisering.oppgavetype == "JFR" &&
                            it.oppgave.bruker != null &&
                            it.oppgave.bruker.identType == IdentType.FOLKEREGISTERIDENT
                    }
                records.forEach { record ->
                    oppgaveService.handleOppgave(
                        record.oppgave.oppgaveId,
                        record.oppgave.bruker!!.ident,
                    )
                }
            } catch (ex: Exception) {
                logger.error("Error processing message", ex)
                delayBeforeRetry()
            }
        }
    }

    private fun delayBeforeRetry() {
        GlobalScope.launch(Dispatchers.IO) {
            logger.info("Waiting for 10 seconds before retrying...")
            delay(10_000)
        }
    }
}

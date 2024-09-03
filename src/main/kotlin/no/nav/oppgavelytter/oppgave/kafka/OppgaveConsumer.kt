package no.nav.oppgavelytter.oppgave.kafka

import no.nav.oppgavelytter.oppgave.OppgaveService
import no.nav.sykdig.applog
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class OppgaveConsumer(
    private val oppgaveTopic: String,
    private val kafkaConsumer: KafkaConsumer<String, OppgaveKafkaAivenRecord>,
    private val oppgaveService: OppgaveService,
    private val applicationState: no.nav.sykdig.ApplicationState,
) {
    val logger = applog()

    suspend fun startConsumer() {
        while (applicationState.ready) {
            try {
                kafkaConsumer.subscribe(listOf(oppgaveTopic))
                consume()
            } catch (ex: Exception) {
                logger.error("error running oppgave-consumer", ex)
                kafkaConsumer.unsubscribe()
                logger.info(
                    "Unsubscribed from topic $oppgaveTopic and waiting for 10 seconds before trying again",
                )
            }
        }
    }

    private fun consume() {
        while (applicationState.ready) {
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
}

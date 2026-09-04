package no.nav.sykdig.utenlandsk.kafka

import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.jsonMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue

@Component
class OppgaveListener(val mottaOppgaverFraKafka: MottaOppgaverFraKafka) {
    val logger = applog()

    @KafkaListener(
        topics = ["\${oppgave.topic}"],
        groupId = "syk-dig-oppgavelytter-consumer",
        properties = ["auto.offset.reset = none"],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val oppgaveRecord: OppgaveKafkaAivenRecord = jsonMapper.readValue(cr.value())
        try {
            val isOppgaveOpprettet =
                oppgaveRecord.hendelse.hendelsestype == Hendelsestype.OPPGAVE_OPPRETTET
            val isValidTema = oppgaveRecord.oppgave.kategorisering.tema in listOf("SYM", "SYK")
            val isCorrectBehandlingstype =
                oppgaveRecord.oppgave.kategorisering.behandlingstype == "ae0106"
            val isCorrectOppgavetype = oppgaveRecord.oppgave.kategorisering.oppgavetype == "JFR"
            val hasValidBruker =
                oppgaveRecord.oppgave.bruker != null &&
                    oppgaveRecord.oppgave.bruker.identType == IdentType.FOLKEREGISTERIDENT

            val isValidOppgave =
                isOppgaveOpprettet &&
                    isValidTema &&
                    isCorrectBehandlingstype &&
                    isCorrectOppgavetype &&
                    hasValidBruker

            if (isValidOppgave) {
                mottaOppgaverFraKafka.behandleOppgave(oppgaveRecord)
            }
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error(
                "Feil ved oppretting av oppgave med oppgaveId: ${oppgaveRecord.oppgave.oppgaveId}",
                e,
            )
            throw e
        }
    }
}

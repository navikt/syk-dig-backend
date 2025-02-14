package no.nav.sykdig.nasjonal.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.nasjonal.clients.MigrationObject
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component


@Component
class NasjonalOppgaveMigrationListener(
    val nasjonalOppgaveService: NasjonalOppgaveService,
) {
    val logger = applog()
    @KafkaListener(
        topics = ["\${smregmigration.topic}"],
        groupId = "syk-dig-migration-consumer",
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )

    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            logger.info("Prosesserer melding: ${cr.value()}")
            val oppgaveRecord: MigrationObject = objectMapper.readValue(cr.value())
            logger.info("migrerer sykmelding med sykmeldingId: ${oppgaveRecord.sykmeldingId}")
            nasjonalOppgaveService.lagreISykDig(oppgaveRecord)
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error("Feil under behandling av melding: ${e.message} ${e.stackTrace}", e)
            throw e
        }
    }
}
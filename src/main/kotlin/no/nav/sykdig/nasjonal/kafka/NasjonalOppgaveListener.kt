package no.nav.sykdig.nasjonal.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component


@Component
class NasjonalOppgaveListener(
    val oppgaveKafkaService: MottaSykmeldingerFraKafka,
) {
    val logger = applog()

    @KafkaListener(
        topics = ["\${smreg.topic}"],
        groupId = "papir-sm-registering-consumer",
        properties = ["auto.offset.reset = none"],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val oppgaveRecord: PapirSmRegistering = objectMapper.readValue(cr.value())
        oppgaveKafkaService.lagreISykDig(oppgaveRecord)
        //oppgaveKafkaService.behandleNasjonalOppgave(oppgaveRecord)
    }
}
package no.nav.sykdig.oppgavemottak

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.objectMapper
import no.nav.sykdig.oppgavemottak.kafka.sykDigOppgaveTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class OppgaveListener(
    val mottaOppgaverFraKafka: MottaOppgaverFraKafka,
) {

    @KafkaListener(
        topics = [sykDigOppgaveTopic],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        mottaOppgaverFraKafka.lagre(cr.key(), objectMapper.readValue(cr.value()))
        acknowledgment.acknowledge()
    }
}

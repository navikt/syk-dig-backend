package no.nav.sykdig.utenlandsk.kafka

import no.nav.sykdig.shared.config.kafka.SYK_DIG_OPPGAVE_TOPIC
import no.nav.sykdig.shared.jsonMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue

@Component
class SykDigOppgaveListener(val mottaOppgaverFraKafka: MottaOppgaverFraKafka) {
    @KafkaListener(
        topics = [SYK_DIG_OPPGAVE_TOPIC],
        groupId = "syk-dig-backend-consumer",
        properties = ["auto.offset.reset = latest"],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        mottaOppgaverFraKafka.lagre(jsonMapper.readValue(cr.value()), cr.key())
        acknowledgment.acknowledge()
    }
}

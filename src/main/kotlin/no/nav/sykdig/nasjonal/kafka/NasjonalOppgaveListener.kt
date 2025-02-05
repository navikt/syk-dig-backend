package no.nav.sykdig.nasjonal.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.nasjonal.services.NasjonalSykmeldingService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component


@Component
class NasjonalOppgaveListener(
    val nasjonalOppgaveService: NasjonalOppgaveService,
    val sykmeldingService: NasjonalSykmeldingService,
    val oppgaveService: NasjonalOppgaveService
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
        logger.info("Processing record with key: ${cr.key()}")
        if (cr.value() == null){
            logger.info(
                "Mottatt tombstone for sykmelding med id ${cr.key()}"
            )
            val deletedSykmeldingRows = sykmeldingService.deleteSykmelding(cr.key())
            val deletedOppgaveRows = oppgaveService.deleteOppgave(cr.key())
            if (deletedSykmeldingRows > 0 && deletedOppgaveRows > 0){
                logger.info("Slettet sykmelding med id ${cr.key()} og tilhørende historikk")
            }
            acknowledgment.acknowledge()
            return
        }
        val oppgaveRecord: PapirSmRegistering = objectMapper.readValue(cr.value())
        logger.info("behandler sykmelding med sykmeldingId: ${oppgaveRecord.sykmeldingId}")
        nasjonalOppgaveService.behandleNasjonalOppgaveFraKafka(oppgaveRecord)
        acknowledgment.acknowledge()
    }
}
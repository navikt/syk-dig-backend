package no.nav.sykdig.nasjonal.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.nasjonal.services.NasjonalSykmeldingService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.objectMapper
import no.nav.sykdig.utenlandsk.services.SykmeldingService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap


@Component
class NasjonalOppgaveListener(
    val oppgaveKafkaService: MottaSykmeldingerFraKafka,
    val sykmeldingService: NasjonalSykmeldingService,
    val oppgaveService: NasjonalOppgaveService
) {
    val logger = applog()
    val processedRecords = ConcurrentHashMap.newKeySet<String>()

    @KafkaListener(
        topics = ["\${smreg.topic}"],
        groupId = "papir-sm-consumer-5",
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        if (!processedRecords.add(cr.key())) {
            logger.info("Duplicate record detected with key: ${cr.key()}, skipping processing.")
            acknowledgment.acknowledge()
            return
        }

        logger.info("Processing record with key: ${cr.key()}")
        if (cr.value() == null){
            logger.info(
                "Mottatt tombstone for sykmelding med id ${cr.key()}"
            )
            val deletedSykmeldingRows = sykmeldingService.deleteSykmelding(cr.key())
            val deletedOppgaveRows = oppgaveService.deleteOppgave(cr.key())
            if (deletedSykmeldingRows >0 && deletedOppgaveRows){
                logger.info("Slettet sykmelding med id ${cr.key()} og tilhørende historikk")
            }
            return
        }
        val oppgaveRecord: PapirSmRegistering = objectMapper.readValue(cr.value())
        logger.info("migrerer sykmelding med sykmeldingId: ${oppgaveRecord.sykmeldingId} and datoOpprettet ${oppgaveRecord.datoOpprettet} {}", kv("object", oppgaveRecord))
        oppgaveKafkaService.lagreISykDig(oppgaveRecord)
        //oppgaveKafkaService.behandleNasjonalOppgave(oppgaveRecord)
        acknowledgment.acknowledge()
    }
}
package no.nav.sykdig.oppgavemottak

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.saf.SafDocumentClient
import no.nav.sykdig.logger
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.objectMapper
import no.nav.sykdig.oppgavemottak.kafka.sykDigOppgaveTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class UpdateDocumentListener(
    private val safDocumentClient: SafDocumentClient,
    private val oppgaveRepository: OppgaveRepository,
) {
    private val log = logger()

    @KafkaListener(
        topics = [sykDigOppgaveTopic],
        properties = ["auto.offset.reset = earliest"],
        containerFactory = "aivenKafkaListenerContainerFactory",
        groupId = "syk-dig-update-document-consumer",
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val oppgave: DigitaliseringsoppgaveKafka = objectMapper.readValue(cr.value())
        val oppgaveDokumenter = (oppgave.dokumenter ?: emptyList()).map {
            DokumentDbModel(
                dokumentInfoId = it.dokumentInfoId,
                tittel = it.tittel,
            )
        }
        val safDocuments = safDocumentClient.getDokumenter(oppgave.journalpostId).map {
            DokumentDbModel(
                dokumentInfoId = it.dokumentInfoId,
                tittel = it.tittel,
            )
        }

        if (safDocuments.isEmpty()) {
            log.info("Fant ikke journalpost for oppgave: ${oppgave.oppgaveId} - journalpost: ${oppgave.journalpostId}")
            acknowledgment.acknowledge()
            return
        }

        if (safDocuments.toSet() == oppgaveDokumenter.toSet()) {
            log.info("Documents are equal, skipping oppgave ${oppgave.oppgaveId}")
            acknowledgment.acknowledge()
            return
        }

        log.info("Oppdaterer dokumenter for oppgave ${oppgave.oppgaveId} fra ${oppgave.source}, antall dokumenter ${safDocuments.size}")
        oppgaveRepository.updateOppgaveDokumenter(oppgave.oppgaveId, safDocuments)
        acknowledgment.acknowledge()
    }
}

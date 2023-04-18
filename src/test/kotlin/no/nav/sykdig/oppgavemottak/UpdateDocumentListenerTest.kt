package no.nav.sykdig.oppgavemottak

import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.saf.SafDocumentClient
import no.nav.sykdig.digitalisering.saf.graphql.SafDocument
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.objectMapper
import no.nav.sykdig.utils.toOppgaveDbModel
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.springframework.kafka.support.Acknowledgment
import java.time.OffsetDateTime
import java.util.UUID

class UpdateDocumentListenerTest {

    val safDocumentClient = Mockito.mock(SafDocumentClient::class.java)
    val oppgaveRepository = Mockito.mock(OppgaveRepository::class.java)
    val updateDocumentListener = UpdateDocumentListener(safDocumentClient, oppgaveRepository)

    @BeforeEach
    fun setup() {
        Mockito.reset(safDocumentClient, oppgaveRepository)
    }

    @Test
    fun updateDocuments() {
        val oppgave = DigitaliseringsoppgaveKafka(
            oppgaveId = "oppgaveid-1",
            fnr = "1",
            dokumentInfoId = "id",
            journalpostId = "journalpost-1",
            dokumenter = null,
            type = "utland",
        )
        val oppgaveDbModel = toOppgaveDbModel(oppgave, OffsetDateTime.now(), UUID.randomUUID().toString())
        `when`(oppgaveRepository.getOppgave("oppgaveid-1")).thenReturn(oppgaveDbModel)
        val dokumenter = listOf(DokumentDbModel("1", "tittel"))
        `when`(safDocumentClient.getDokumenter("journalpost-1")).thenReturn(dokumenter.map { SafDocument(it.dokumentInfoId, it.tittel) })
        val consumerRecord = ConsumerRecord("topic", 1, 1L, "Id", objectMapper.writeValueAsString(oppgave))
        updateDocumentListener.listen(consumerRecord, Acknowledgment {})
        Mockito.verify(oppgaveRepository, times(1)).updateOppgaveDokumenter("oppgaveid-1", dokumenter)
    }

    @Test
    fun notUpdateDocuments() {
        val oppgave = DigitaliseringsoppgaveKafka(
            oppgaveId = "oppgaveid-2",
            fnr = "2",
            dokumentInfoId = "1",
            journalpostId = "journalpost-2",
            dokumenter = listOf(DokumentKafka(dokumentInfoId = "1", tittel = "tittel")),
            type = "utland",
        )
        val oppgaveDbModel = toOppgaveDbModel(oppgave, OffsetDateTime.now(), UUID.randomUUID().toString())
        `when`(oppgaveRepository.getOppgave("oppgaveid-2")).thenReturn(oppgaveDbModel)
        val dokumenter = listOf(DokumentDbModel("1", "tittel"))
        Mockito.`when`(safDocumentClient.getDokumenter("journalpost-2"))
            .thenReturn(dokumenter.map { SafDocument(it.dokumentInfoId, it.tittel) })
        val consumerRecord = ConsumerRecord("topic", 1, 1L, "Id", objectMapper.writeValueAsString(oppgave))
        updateDocumentListener.listen(consumerRecord, Acknowledgment {})
        Mockito.verify(oppgaveRepository, times(0)).updateOppgaveDokumenter("oppgaveid-2", dokumenter)
    }

    @Test
    fun updateDocumentsWhenDifferent() {
        val oppgave = DigitaliseringsoppgaveKafka(
            oppgaveId = "oppgaveid-3",
            fnr = "1",
            dokumentInfoId = "id",
            dokumenter = listOf(DokumentKafka(dokumentInfoId = "2", tittel = "tittel")),
            type = "utland",
            journalpostId = "3",
        )
        val oppgaveDbModel = toOppgaveDbModel(oppgave, OffsetDateTime.now(), UUID.randomUUID().toString())
        `when`(oppgaveRepository.getOppgave("oppgaveid-3")).thenReturn(oppgaveDbModel)
        val dokumenter = listOf(DokumentDbModel("1", "tittel2"))
        Mockito.`when`(safDocumentClient.getDokumenter("3")).thenReturn(dokumenter.map { SafDocument(it.dokumentInfoId, it.tittel) })
        val consumerRecord = ConsumerRecord("topic", 1, 1L, "Id", objectMapper.writeValueAsString(oppgave))
        updateDocumentListener.listen(consumerRecord, Acknowledgment {})
        Mockito.verify(oppgaveRepository, times(1)).updateOppgaveDokumenter("oppgaveid-3", dokumenter)
    }
}

package no.nav.sykdig.dokarkiv

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.sykdig.utenlandsk.db.OppgaveRepository
import no.nav.sykdig.utenlandsk.models.DokumentDbModel
import no.nav.sykdig.utenlandsk.models.OppgaveDbModel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class DocumentServiceTest {
    private val oppgaveRepository = Mockito.mock(OppgaveRepository::class.java)
    private val dokarkivClient = Mockito.mock(DokarkivClient::class.java)

    private val documentService = DocumentService(oppgaveRepository, dokarkivClient)

    @BeforeEach
    fun runBeforeEach() {
        Mockito.reset(oppgaveRepository, dokarkivClient)
    }

    @Test
    fun shouldUpdateOneDocument() {
        val gammelTittel = "gammel-tittel"
        val nyTittel = "ny-tittel"
        val dokumentInfoId = "1"
        val oppgaveId = "1"
        val journalpostId = "journalpost1"
        val fnr = "fnr"
        `when`(oppgaveRepository.getOppgave(oppgaveId))
            .thenReturn(
                getOppgave(
                    oppgaveId,
                    fnr,
                    journalpostId,
                    listOf(DokumentDbModel(dokumentInfoId, gammelTittel)),
                )
            )

        documentService.updateDocumentTitle(oppgaveId, dokumentInfoId, nyTittel)
        val newListOfDocuments = listOf(DokumentDbModel(dokumentInfoId, nyTittel))
        verify(oppgaveRepository, times(1)).updateDocuments(oppgaveId, newListOfDocuments)

        verify(dokarkivClient, times(1)).updateDocument(journalpostId, dokumentInfoId, nyTittel)
        verify(oppgaveRepository, times(1)).updateDocuments(oppgaveId, newListOfDocuments)
    }

    @Test
    fun shouldUpdateOneOfManyDocuments() {
        val oppgaveId = "2"
        val journalpostId = "journalpost2"
        val dokumentInfoId = "4"

        val documents =
            listOf(
                DokumentDbModel("1", "1"),
                DokumentDbModel("2", "2"),
                DokumentDbModel("3", "3"),
                DokumentDbModel("4", "4"),
                DokumentDbModel("5", "5"),
            )
        val newDocuments =
            listOf(
                DokumentDbModel("1", "1"),
                DokumentDbModel("2", "2"),
                DokumentDbModel("3", "3"),
                DokumentDbModel("4", "4-ny"),
                DokumentDbModel("5", "5"),
            )
        `when`(oppgaveRepository.getOppgave(oppgaveId))
            .thenReturn(getOppgave(oppgaveId, "fnr", journalpostId, documents))

        documentService.updateDocumentTitle(oppgaveId, dokumentInfoId, "4-ny")

        verify(dokarkivClient, times(1)).updateDocument(journalpostId, "4", "4-ny")
        verify(oppgaveRepository, times(1)).updateDocuments(oppgaveId, newDocuments)
    }

    @Test
    fun shouldNotUpdateWhenIncorrectId() {
        val oppgaveId = "3"
        val journalpostId = "journalpost3"
        val dokumentInfoId = "3"

        val documents = listOf(DokumentDbModel("1", "1"), DokumentDbModel("2", "2"))

        `when`(oppgaveRepository.getOppgave(oppgaveId))
            .thenReturn(getOppgave(oppgaveId, "fnr", journalpostId, documents))
        assertThrows<DgsEntityNotFoundException> {
            documentService.updateDocumentTitle(oppgaveId, dokumentInfoId, "ny-tittel")
        }

        verify(dokarkivClient, times(0)).updateDocument(anyString(), anyString(), anyString())
        verify(oppgaveRepository, times(0)).updateDocuments(anyString(), anyList())
    }

    @Test
    fun notUpdateWhenTittelIsEqualToOldTittel() {
        val oppgaveId = "4"
        val journalpostId = "journalpost4"
        val dokumentInfoId = "2"

        val documents = listOf(DokumentDbModel("1", "1"), DokumentDbModel("2", "2"))

        `when`(oppgaveRepository.getOppgave(oppgaveId))
            .thenReturn(getOppgave(oppgaveId, "fnr", journalpostId, documents))

        documentService.updateDocumentTitle(oppgaveId, dokumentInfoId, "2")

        verify(dokarkivClient, times(0)).updateDocument(anyString(), anyString(), anyString())
        verify(oppgaveRepository, times(0)).updateDocuments(anyString(), anyList())
    }

    private fun getOppgave(
        oppgaveId: String,
        fnr: String,
        journalpostId: String,
        dokumenter: List<DokumentDbModel>,
    ) =
        OppgaveDbModel(
            oppgaveId,
            fnr,
            journalpostId,
            null,
            dokumenter,
            OffsetDateTime.now(),
            null,
            false,
            null,
            UUID.randomUUID(),
            "UTENLANDS",
            null,
            "",
            OffsetDateTime.now(),
            "test",
        )
}

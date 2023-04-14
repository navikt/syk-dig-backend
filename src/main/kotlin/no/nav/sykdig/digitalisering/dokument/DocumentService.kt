package no.nav.sykdig.digitalisering.dokument

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv.DokarkivClient
import no.nav.sykdig.model.DokumentDbModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DocumentService(
    private val oppgaveRepository: OppgaveRepository,
    private val dokarkivClient: DokarkivClient,
) {
    @Transactional
    fun updateDocumentTitle(oppgaveId: String, document: DokumentDbModel) {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId) ?: throw DgsEntityNotFoundException("Fant ikke oppgave $oppgaveId")
        val journalpostid = oppgave.journalpostId
        val documents = oppgave.dokumenter
        dokarkivClient.updateDocument(journalpostid, document)
    }
}

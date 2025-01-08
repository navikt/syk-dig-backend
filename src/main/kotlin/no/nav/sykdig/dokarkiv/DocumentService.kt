package no.nav.sykdig.dokarkiv

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.sykdig.felles.applog
import no.nav.sykdig.utenlandsk.db.OppgaveRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DocumentService internal constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val dokarkivClient: DokarkivClient,
) {
    val log = applog()

    @Transactional
    fun updateDocumentTitle(
        oppgaveId: String,
        dokumentInfoId: String,
        tittel: String,
    ) {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId) ?: throw DgsEntityNotFoundException("Fant ikke oppgave $oppgaveId")
        val journalpostId = oppgave.journalpostId
        val document =
            oppgave.dokumenter
                .firstOrNull { it.dokumentInfoId == dokumentInfoId }
                ?: throw DgsEntityNotFoundException("Fant ikke dokument $dokumentInfoId")

        if (document.tittel == tittel) {
            log.info("Dokumentet har lik tittel, oppdaterer ikke")
        } else {
            log.info("Oppdaterer tittel fra ${document.tittel} til $tittel")
            val oppdatertDokumenter =
                oppgave.dokumenter.map {
                    if (it.dokumentInfoId == dokumentInfoId) {
                        it.copy(tittel = tittel)
                    } else {
                        it
                    }
                }
            oppgaveRepository.updateDocuments(oppgaveId, oppdatertDokumenter)
            dokarkivClient.updateDocument(
                journalpostid = journalpostId,
                documentId = dokumentInfoId,
                tittel = tittel,
            )
        }
    }
}

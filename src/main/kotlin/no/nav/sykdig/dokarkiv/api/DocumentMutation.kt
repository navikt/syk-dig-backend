package no.nav.sykdig.dokarkiv.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import kotlinx.coroutines.runBlocking
import no.nav.sykdig.dokarkiv.DocumentService
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Document
import org.springframework.security.access.prepost.PreAuthorize

@DgsComponent
class DocumentMutation(
    private val documentService: DocumentService,
) {
    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @DgsMutation(field = DgsConstants.MUTATION.Dokument)
    fun oppdaterDukumentTittel(
        @InputArgument oppgaveId: String,
        @InputArgument dokumentInfoId: String,
        @InputArgument tittel: String,
    ): Document {
        runBlocking { documentService.updateDocumentTitle(oppgaveId, dokumentInfoId, tittel) }
        return Document(
            dokumentInfoId = dokumentInfoId,
            tittel = tittel,
        )
    }
}

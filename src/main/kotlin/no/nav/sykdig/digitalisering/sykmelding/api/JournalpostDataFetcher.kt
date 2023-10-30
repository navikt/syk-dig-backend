package no.nav.sykdig.digitalisering.sykmelding.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Journalpost

@DgsComponent
class JournalpostDataFetcher(
    private val safGraphQlClient: SafJournalpostGraphQlClient,
) {

    // TODO Access control
    // @PreAuthorize("@journalpostOppgaveService.hasAccessToJournalpost(#journalpostId)")
    @DgsQuery(field = DgsConstants.QUERY.Journalpost)
    fun getJournalpostById(
        @InputArgument journalpostId: String,
        dfe: DataFetchingEnvironment,
    ): Journalpost {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)
        return Journalpost(
            journalpostId,
            journalpost.journalpost?.journalstatus?.name ?: "MANGLER_STATUS",
        )
    }
}

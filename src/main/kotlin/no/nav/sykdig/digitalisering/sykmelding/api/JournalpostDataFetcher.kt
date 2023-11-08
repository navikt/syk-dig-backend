package no.nav.sykdig.digitalisering.sykmelding.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Journalpost
import org.springframework.security.access.prepost.PostAuthorize

@DgsComponent
class JournalpostDataFetcher(
    private val safGraphQlClient: SafJournalpostGraphQlClient,
) {

    @PostAuthorize("@oppgaveSecurityService.hasAccessToJournalpost(#id)")
    @DgsQuery(field = DgsConstants.QUERY.Journalpost)
    fun getJournalpostById(
        @InputArgument id: String,
        dfe: DataFetchingEnvironment,
    ): Journalpost {
        val journalpost = safGraphQlClient.getJournalpost(id)
        return Journalpost(
            id,
            journalpost.journalpost?.journalstatus?.name ?: "MANGLER_STATUS",
            dokumenter = journalpost.journalpost?.dokumenter?.map {
                Document(it.tittel ?: "Mangler Tittel", it.dokumentInfoId)
            } ?: emptyList(),
        )
    }
}

package no.nav.sykdig.digitalisering.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.generated.DgsConstants

@DgsComponent
class DocumentDataFetcher() {
    @DgsMutation(field = DgsConstants.MUTATION.Dokument)
    fun oppdaterDukumentTittel(
        @InputArgument oppgaveId: String,
        @InputArgument dokumentId: String,
        @InputArgument tittel: String,
    ) {
    }
}

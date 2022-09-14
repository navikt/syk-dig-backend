package no.nav.sykdig.arbeidsgiver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Arbeidsgiver
import no.nav.sykdig.generated.types.ArbeidsgiverInput
import org.springframework.security.access.annotation.Secured
import org.springframework.security.access.prepost.PreAuthorize

@DgsComponent
class ArbeidsgiverDataFetcher {
    @DgsQuery
    fun arbeidsgivere(): List<Arbeidsgiver> {
        return listOf(
            Arbeidsgiver(navn = "Navn"),
            Arbeidsgiver(navn = "navn1"),
            Arbeidsgiver(navn = "navn2"),
            Arbeidsgiver(navn = "navn3"),
            Arbeidsgiver(navn = "navn4"),
        )
    }

    @DgsData(parentType = DgsConstants.ARBEIDSGIVER.TYPE_NAME)
    fun orgnummer(dfe: DgsDataFetchingEnvironment): String {
        return "orgnummer"
    }

    @DgsMutation
    fun minMutation(@InputArgument arbeidsgiver: ArbeidsgiverInput): Arbeidsgiver {
        return Arbeidsgiver(navn = arbeidsgiver.navn, orgnummer = arbeidsgiver.orgnummer)
    }
}

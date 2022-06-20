package no.nav.sykdig.utenlandsk

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument

@DgsComponent
class UtenlandsSykmeldingDataFetcher {

    @DgsQuery(field = "utenlandssykmelding")
    fun getUtenlandskeSykmeldinger(@InputArgument id: String): List<UtenlandsSykmelding> {
        return listOf(UtenlandsSykmelding(fnr = "fnr", id = "id"))
    }
}

data class UtenlandsSykmelding(
    val fnr: String,
    val id: String
)
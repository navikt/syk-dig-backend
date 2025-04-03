package no.nav.sykdig.nasjonal.services

import no.nav.sykdig.generated.types.Navn
import no.nav.sykdig.pdl.client.PdlClient
import no.nav.sykdig.pdl.client.graphql.PdlResponse
import org.springframework.stereotype.Service

@Service
class PasientNavnService(
    private val pdlClient: PdlClient
) {
    fun getPersonNavn(
        id: String,
        callId: String,
    ): Navn {
        val pdlResponse = pdlClient.getPerson(id, callId)
        return mapPdlResponseTilPersonNavn(pdlResponse)
    }

    private fun mapPdlResponseTilPersonNavn(pdlResponse: PdlResponse): Navn {
        val navn = pdlResponse.hentPerson!!.navn.first()

        return Navn(
            fornavn = navn.fornavn,
            mellomnavn = navn.mellomnavn,
            etternavn = navn.etternavn,
        )
    }
}
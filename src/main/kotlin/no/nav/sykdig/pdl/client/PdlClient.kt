package no.nav.sykdig.pdl.client

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.felles.applog
import no.nav.sykdig.pdl.client.graphql.PDL_QUERY
import no.nav.sykdig.pdl.client.graphql.PdlResponse
import no.nav.sykdig.pdl.client.graphql.mapToPdlResponse
import no.nav.sykdig.felles.securelog
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class PdlClient(
    private val pdlGraphQlClient: CustomGraphQLClient,
) {
    val log = applog()
    val securelog = securelog()

    @Retryable
    fun getPerson(
        id: String,
        callId: String,
    ): PdlResponse {
        try {
            val response = pdlGraphQlClient.executeQuery(PDL_QUERY, mapOf("ident" to id))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra PDL: ${it.message} for $callId") }

            val pdlResponse: PdlResponse = mapToPdlResponse(response.json)

            if (pdlResponse.hentPerson == null) {
                log.error("Fant ikke person i PDL $callId")
                securelog.info(("Fant ikke person i PDL $callId, fnr: $id"))
                throw RuntimeException("Fant ikke person i PDL")
            }

            if (pdlResponse.hentPerson.navn.isEmpty()) {
                log.error("Fant ikke navn for person i PDL $callId")
                securelog.info(("Fant ikke navn for person i PDL $callId, fnr: $id"))
                throw RuntimeException("Fant ikke navn for person i PDL")
            }
            if (pdlResponse.identer == null || pdlResponse.identer.identer.firstOrNull { it.gruppe == "AKTORID" } == null) {
                log.error("Fant ikke aktørid for person i PDL $callId")
                securelog.info(("Fant ikke aktørid for person i PDL $callId, fnr: $id"))
                throw RuntimeException("Fant ikke aktørid for person i PDL")
            }

            log.info("Hentet person for callId: $callId")
            return pdlResponse
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til PDL", e)
            throw e
        }
    }
}

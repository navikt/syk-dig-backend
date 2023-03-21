package no.nav.sykdig.digitalisering.pdl.client

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.client.CustomGraphQLClient
import com.netflix.graphql.dgs.client.GraphQLResponse
import no.nav.sykdig.digitalisering.pdl.client.graphql.PDL_QUERY
import no.nav.sykdig.digitalisering.pdl.client.graphql.PdlResponse
import no.nav.sykdig.logger
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class PdlClient(
    private val pdlGraphQlClient: CustomGraphQLClient,
) {
    val log = logger()
    val securelog = securelog()

    @Retryable
    fun hentPerson(fnr: String, sykmeldingId: String): PdlResponse {
        try {
            val response = pdlGraphQlClient.executeQuery(PDL_QUERY, mapOf("ident" to fnr))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra PDL: ${it.message} for $sykmeldingId") }

            securelog.info("Repsone: ${response.json}")

            val pdlResponse: PdlResponse = mapToPdlResponse(response)

            if (pdlResponse.hentPerson == null) {
                log.error("Fant ikke person i PDL $sykmeldingId")
                securelog.info(("Fant ikke person i PDL $sykmeldingId, fnr: $fnr"))
                throw RuntimeException("Fant ikke person i PDL")
            }

            if (pdlResponse.hentPerson.navn.isEmpty()) {
                log.error("Fant ikke navn for person i PDL $sykmeldingId")
                securelog.info(("Fant ikke navn for person i PDL $sykmeldingId, fnr: $fnr"))
                throw RuntimeException("Fant ikke navn for person i PDL")
            }
            if (pdlResponse.identer == null || pdlResponse.identer.identer.firstOrNull { it.gruppe == "AKTORID" } == null) {
                log.error("Fant ikke aktørid for person i PDL $sykmeldingId")
                securelog.info(("Fant ikke aktørid for person i PDL $sykmeldingId, fnr: $fnr"))
                throw RuntimeException("Fant ikke aktørid for person i PDL")
            }

            log.info("Hentet person for $sykmeldingId")
            return pdlResponse
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til PDL", e)
            throw e
        }
    }

    fun mapToPdlResponse(graphQLResponse: GraphQLResponse): PdlResponse =
        objectMapper.readValue(graphQLResponse.json)
}

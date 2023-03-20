package no.nav.sykdig.digitalisering.pdl.client

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.digitalisering.pdl.client.graphql.PDL_QUERY
import no.nav.sykdig.digitalisering.pdl.client.graphql.PdlResponse
import no.nav.sykdig.logger
import no.nav.sykdig.objectMapper
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class PdlClient(
    private val pdlGraphQlClient: CustomGraphQLClient,
) {
    val log = logger()

    @Retryable
    fun hentPerson(fnr: String, sykmeldingId: String): PdlResponse {
        try {
            val response = pdlGraphQlClient.executeQuery(PDL_QUERY, mapOf("ident" to fnr))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra PDL: ${it.message} for $sykmeldingId") }

            val pdlResponse: PdlResponse = objectMapper.readValue(response.json)

            if (pdlResponse.hentPerson == null || pdlResponse.hentPerson.navn.isEmpty()) {
                log.error("Fant ikke navn for person i PDL $sykmeldingId")
                throw RuntimeException("Fant ikke navn for person i PDL")
            }
            if (pdlResponse.identer == null || pdlResponse.identer.identer.firstOrNull { it.gruppe == "AKTORID" } == null) {
                log.error("Fant ikke aktørid for person i PDL $sykmeldingId")
                throw RuntimeException("Fant ikke aktørid for person i PDL")
            }

            log.info("Hentet person for $sykmeldingId")
            return pdlResponse
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til PDL", e)
            throw e
        }
    }
}

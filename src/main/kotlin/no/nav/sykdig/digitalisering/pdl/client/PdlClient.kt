package no.nav.sykdig.digitalisering.pdl.client

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.digitalisering.pdl.client.graphql.PDL_QUERY
import no.nav.sykdig.digitalisering.pdl.client.graphql.PdlPerson
import no.nav.sykdig.digitalisering.pdl.client.graphql.PdlQuery
import no.nav.sykdig.logger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class PdlClient(
    private val pdlGraphQlClient: CustomGraphQLClient
) {
    val log = logger()

    @Retryable
    fun hentPerson(fnr: String, sykmeldingId: String): PdlPerson {
        try {
            val response = pdlGraphQlClient.executeQuery(PDL_QUERY, mapOf("ident" to fnr))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra PDL: ${it.message} for $sykmeldingId") }

            val pdlResponse = response.dataAsObject(PdlQuery::class.java)
            val pdlPerson = pdlResponse.hentPerson

            if (pdlPerson == null || pdlPerson.navn.isEmpty()) {
                log.error("Fant ikke navn for person i PDL $sykmeldingId")
                throw RuntimeException("Fant ikke navn for person i PDL")
            }

            log.info("Hentet person for $sykmeldingId")
            return pdlPerson
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til PDL", e)
            throw e
        }
    }
}

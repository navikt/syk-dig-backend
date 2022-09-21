package no.nav.sykdig.digitalisering.pdl.client

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.HttpResponse
import no.nav.sykdig.digitalisering.pdl.client.graphql.PDL_QUERY
import no.nav.sykdig.generated.types.PdlPerson
import no.nav.sykdig.generated.types.PdlQuery
import no.nav.sykdig.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class PdlClient(
    @Value("\${pdl.url}") private val url: String,
    private val pdlRestTemplate: RestTemplate
) {
    private val temaHeader = "TEMA"
    private val tema = "SYM"

    val log = logger()

    val client = GraphQLClient.createCustom(url) { url, _, body ->
        val httpHeaders = HttpHeaders()
        httpHeaders[temaHeader] = tema
        httpHeaders.contentType = MediaType.APPLICATION_JSON

        val response = pdlRestTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)

        HttpResponse(response.statusCodeValue, response.body)
    }

    @Retryable
    fun hentPerson(fnr: String, sykmeldingId: String): PdlPerson {
        try {
            val response = client.executeQuery(PDL_QUERY, mapOf("ident" to fnr))

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

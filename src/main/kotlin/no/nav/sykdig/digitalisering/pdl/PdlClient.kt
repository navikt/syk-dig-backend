package no.nav.sykdig.digitalisering.pdl

import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.HttpResponse
import no.nav.sykdig.digitalisering.pdl.graphql.PdlQuery
import no.nav.sykdig.digitalisering.pdl.graphql.PdlResponse
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
    fun hentPerson(fnr: String, sykmeldingId: String): Person {
        try {
            val pdlResponse =
                client.executeQuery(PdlQuery(fnr).getQuery()).extractValueAsObject("data", PdlResponse::class.java)

            if (pdlResponse.hentPerson == null || pdlResponse.hentPerson.navn.isNullOrEmpty()) {
                log.error("Fant ikke navn for person i PDL $sykmeldingId")
                throw RuntimeException("Fant ikke navn for person i PDL")
            }

            val navn = pdlResponse.hentPerson.navn.first()

            log.info("Hentet person med navn: $navn")
            return Person(
                fnr = fnr,
                navn = Navn(
                    fornavn = navn.fornavn,
                    mellomnavn = navn.mellomnavn,
                    etternavn = navn.etternavn
                )
            )
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til PDL", e)
            throw e
        }
    }
}

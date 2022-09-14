package no.nav.sykdig.digitalisering.saf

import no.nav.sykdig.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@Component
class SafClient(
    @Value("\${saf.url}") private val url: String,
    private val safRestTemplate: RestTemplate
) {
    val log = logger()

    @Retryable
    fun hentPdfFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        sykmeldingId: String
    ): ByteArray {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers.accept = listOf(MediaType.APPLICATION_PDF)
        headers["Nav-Callid"] = sykmeldingId
        headers["Nav-Consumer-Id"] = "syk-dig-backend"

        log.debug("Henter pdf")
        try {
            val response = safRestTemplate.exchange(
                "$url/rest/hentdokument/$journalpostId/$dokumentInfoId/ARKIV",
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                ByteArray::class.java
            )
            log.debug("Har hentet pdf")
            return response.body ?: throw RuntimeException("Tomt svar fra SAF for journalpostId $journalpostId")
        } catch (e: HttpClientErrorException) {
            if (e.rawStatusCode == 401 || e.rawStatusCode == 403) {
                log.warn("Veileder har ikke tilgang til journalpostId $journalpostId: ${e.message}")
            } else {
                log.error("HttpClientErrorException med responskode ${e.rawStatusCode} fra SAF: ${e.message}", e)
            }
            throw RuntimeException("HttpClientErrorException fra SAF")
        } catch (e: HttpServerErrorException) {
            log.error("HttpServerErrorException med responskode ${e.rawStatusCode} fra SAF: ${e.message}", e)
            throw RuntimeException("HttpServerErrorException fra SAF")
        }
    }
}

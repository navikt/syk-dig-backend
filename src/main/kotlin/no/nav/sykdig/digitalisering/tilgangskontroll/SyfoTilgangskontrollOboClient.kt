package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.logger
import no.nav.sykdig.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.GET
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Component
class SyfoTilgangskontrollOboClient(
    @Value("\${syfotilgangskontroll.url}") private val url: String,
    private val syfotilgangskontrollRestTemplate: RestTemplate,
) {
    companion object {
        const val ACCESS_TO_USER_WITH_AZURE_V2_PATH = "/syfo-tilgangskontroll/api/tilgang/navident/person"
        const val NAV_PERSONIDENT_HEADER = "nav-personident"
    }

    val log = logger()
    val securelog = securelog()

    @Retryable
    fun sjekkTilgangVeileder(fnr: String): Boolean {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers[NAV_PERSONIDENT_HEADER] = fnr

        try {
            val response = syfotilgangskontrollRestTemplate.exchange(
                accessToUserV2Url(),
                GET,
                HttpEntity<Any>(headers),
                String::class.java,
            ).also { securelog.info("Headers: ${it.headers}") }
            return response.statusCode.is2xxSuccessful
        } catch (e: HttpClientErrorException) {
            return if (e.statusCode.value() == 403) {
                false
            } else {
                log.error("HttpClientErrorException mot tilgangskontroll", e)
                false
            }
        }
    }

    fun accessToUserV2Url(): String {
        return "$url$ACCESS_TO_USER_WITH_AZURE_V2_PATH"
    }
}

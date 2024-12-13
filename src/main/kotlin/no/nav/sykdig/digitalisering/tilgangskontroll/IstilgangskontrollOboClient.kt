package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.applog
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
class IstilgangskontrollOboClient(
    @Value("\${istilgangskontroll.url}") private val url: String,
    private val istilgangskontrollRestTemplate: RestTemplate,
) {
    companion object {
        const val ACCESS_TO_USER_WITH_AZURE_V2_PATH = "/api/tilgang/navident/person"
        const val SUPER_USER_ACCESS_WITH_V2_PATH = "/api/tilgang/navident/person/papirsykmelding"
        const val NAV_PERSONIDENT_HEADER = "nav-personident"
    }

    val log = applog()

    @Retryable
    fun sjekkTilgangVeileder(fnr: String): Boolean {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers[NAV_PERSONIDENT_HEADER] = fnr

        try {
            val response =
                istilgangskontrollRestTemplate.exchange(
                    accessToUserV2Url(),
                    GET,
                    HttpEntity<Any>(headers),
                    String::class.java,
                )
            return response.statusCode.is2xxSuccessful
        } catch (e: HttpClientErrorException) {
            return if (e.statusCode.value() == 403) {
                log.error("istilgangskontroll returnerte 403", e)
                false
            } else {
                log.error("HttpClientErrorException mot tilgangskontroll", e)
                false
            }
        }
    }

    @Retryable
    fun sjekkSuperBrukerTilgangVeileder(fnr: String): Boolean {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers[NAV_PERSONIDENT_HEADER] = fnr

        try {
            val response =
                istilgangskontrollRestTemplate.exchange(
                    superUserAccessToV2UrlForKorrigerePapirSykmelding(),
                    GET,
                    HttpEntity<Any>(headers),
                    String::class.java,
                )
            return response.statusCode.is2xxSuccessful
        } catch (e: HttpClientErrorException) {
            return if (e.statusCode.value() == 403) {
                log.error("istilgangskontroll returnerte 403", e)
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

    fun superUserAccessToV2UrlForKorrigerePapirSykmelding(): String {
        return "$url$SUPER_USER_ACCESS_WITH_V2_PATH"
    }
}

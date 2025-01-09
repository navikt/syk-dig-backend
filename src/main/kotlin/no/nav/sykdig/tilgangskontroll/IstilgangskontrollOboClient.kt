package no.nav.sykdig.tilgangskontroll

import no.nav.sykdig.shared.applog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class IstilgangskontrollOboClient(
    @Value("\${istilgangskontroll.url}") private val url: String,
    private val istilgangskontrollWebClient: WebClient,
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

        return try {
            val response = istilgangskontrollWebClient.get()  // Changed from exchange to get()
                .uri(accessToUserV2Url())
                .headers { it.addAll(headers) }
                .retrieve()
                .toEntity(String::class.java)
                .block()  // Blocking for synchronous response (you can use subscribe() for asynchronous)

            response?.statusCode?.is2xxSuccessful == true
        } catch (e: WebClientResponseException) {
            if (e.statusCode == HttpStatus.FORBIDDEN) {
                log.error("istilgangskontroll returnerte 403", e)
                false
            } else {
                log.error("WebClientResponseException mot tilgangskontroll", e)
                false
            }
        }
    }

    @Retryable
    fun sjekkSuperBrukerTilgangVeileder(fnr: String): Boolean {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers[NAV_PERSONIDENT_HEADER] = fnr

        return try {
            val response = istilgangskontrollWebClient.get()  // Changed from exchange to get()
                .uri(superUserAccessToV2UrlForKorrigerePapirSykmelding())
                .headers { it.addAll(headers) }
                .retrieve()
                .toEntity(String::class.java)
                .block()  // Blocking for synchronous response (you can use subscribe() for asynchronous)

            response?.statusCode?.is2xxSuccessful == true
        } catch (e: WebClientResponseException) {
            if (e.statusCode == HttpStatus.FORBIDDEN) {
                log.error("istilgangskontroll returnerte 403", e)
                false
            } else {
                log.error("WebClientResponseException mot tilgangskontroll", e)
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

package no.nav.sykdig.tilgangskontroll

import no.nav.sykdig.shared.applog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange

@Component
class TilgangskontrollOboClient(
    @param:Value("\${tilgangskontroll.url}") private val url: String,
    private val tilgangskontrollRestTemplate: RestTemplate,
) {
    companion object {
        const val TILGANGSMASKIN_API_PATH = "/api/v1/komplett"
    }

    val log = applog()

    @Retryable
    fun sjekkTilgangVeileder(fnr: String): Boolean {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        try {
            val response =
                tilgangskontrollRestTemplate.exchange<String>(
                    accessToUserV2Url(),
                    POST,
                    HttpEntity(fnr, headers),
                )
            log.info("tilgangsmaskin svarer med httpResponse status kode: ${response.statusCode}")
            return response.statusCode == HttpStatus.NO_CONTENT
        } catch (e: HttpClientErrorException) {
            return if (e.statusCode.value() == 403) {
                log.warn("tilgangskontroll returnerte 403", e)
                false
            } else {
                log.error("HttpClientErrorException mot tilgangskontroll", e)
                false
            }
        }
    }

    fun accessToUserV2Url(): String {
        return "$url$TILGANGSMASKIN_API_PATH"
    }
}

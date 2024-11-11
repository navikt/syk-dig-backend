package no.nav.sykdig.digitalisering.helsenett.client

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.SykmelderNotFoundException
import no.nav.sykdig.digitalisering.exceptions.UnauthorizedException
import no.nav.sykdig.digitalisering.helsenett.Behandler
import no.nav.sykdig.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.io.IOException

@Component
class HelsenettClient(
    @Value("\${helsenett.url}") private val helsenettUrl: String,
    private val helsenettM2mRestTemplate: RestTemplate,
) {
    val log = applog()
    val securelog = securelog()

    fun getBehandler(
        hprNummer: String,
        callId: String,
    ): Behandler {
        log.info("Henter behandler fra syfohelsenettproxy for callId {}", callId)

        val headers = HttpHeaders()
        headers["Nav-CallId"] = callId
        headers["hprNummer"] = hprNummer

        val response = helsenettM2mRestTemplate.exchange(
            "$helsenettUrl/api/v2/behandlerMedHprNummer",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            Behandler::class.java,
        )
        return response.body ?: throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Behandler ikke funnet for hprNummer $hprNummer")
    }
}


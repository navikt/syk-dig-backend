package no.nav.sykdig.nasjonal.helsenett.client

import no.nav.sykdig.felles.applog
import no.nav.sykdig.felles.exceptions.SykmelderNotFoundException
import no.nav.sykdig.nasjonal.helsenett.Behandler
import no.nav.sykdig.felles.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

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

        val response = try {
            helsenettM2mRestTemplate.exchange(
                "$helsenettUrl/api/v2/behandlerMedHprNummer",
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                Behandler::class.java,
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                throw SykmelderNotFoundException("Behandler ikke funnet for hprNummer $hprNummer")
            }
            throw e
        }
        return response.body ?: throw SykmelderNotFoundException("Behandler ikke funnet for hprNummer $hprNummer")
    }
}


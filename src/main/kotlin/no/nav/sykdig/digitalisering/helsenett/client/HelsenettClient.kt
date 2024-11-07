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
import org.springframework.web.client.RestTemplate
import java.io.IOException

@Component
class HelsenettClient(
    @Value("\$helsenett.url") private val helsenettUrl: String,
    private val helsenettClientRestTemplate: RestTemplate,
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
        headers["hprNummer"] = padHpr(hprNummer)

        // antakelse om at exceptions blir plukket opp av global exceptionhandler
        // vi nullchecker hpr tidligere i løpet
        val response =
            helsenettClientRestTemplate.exchange(
                "$helsenettUrl/api/v2/behandlerMedHprNummer",
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                Behandler::class.java,
            )

        when (response.statusCode) {
            HttpStatus.OK -> true
            HttpStatus.INTERNAL_SERVER_ERROR -> throw IOException("Syfohelsenettproxy svarte med feilmelding for $callId")
            HttpStatus.NOT_FOUND -> throw SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprNummer")
            HttpStatus.UNAUTHORIZED -> throw UnauthorizedException("Norsk helsenett returnerte Unauthorized ved oppslag av HPR-nummer $hprNummer")
            else -> throw RuntimeException("En ukjent feil oppsto ved ved henting av behandler $callId")
        }

        log.info("Hentet behandler for callId {}", callId)
        return response.body!!
    }

    fun padHpr(hprnummer: String): String? {
        if (hprnummer.length < 9) {
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }
}

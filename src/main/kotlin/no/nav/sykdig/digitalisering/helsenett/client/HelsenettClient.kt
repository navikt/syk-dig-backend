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
    private val helsenettRestTemplate: RestTemplate
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

        // antakelse om at exceptions blir plukket opp av global exceptionhandler
        // vi nullchecker hpr tidligere i løpet
        val response =
            helsenettRestTemplate.exchange(
                "$helsenettUrl/api/v2/behandlerMedHprNummer",
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                Behandler::class.java,
            )

        return when (response.statusCode) {
            HttpStatus.OK -> {
                log.info("Hentet behandler for callId {}", callId)
                response.body ?: throw SykmelderNotFoundException("Fant ikke behandler for callId $callId, hprnr $hprNummer")
            }
            HttpStatus.INTERNAL_SERVER_ERROR -> {
                log.error("Syfohelsenettproxy svarte med feilmelding for callId {}", callId)
                throw IOException("Syfohelsenettproxy svarte med feilmelding for $callId")
            }
            HttpStatus.NOT_FOUND -> {
                log.warn("Fant ikke behandler for HprNummer $hprNummer, callId $callId")
                throw SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprNummer")
            }
            HttpStatus.UNAUTHORIZED -> {
                log.warn("Syfohelsenettproxy returnerte Unauthorized for henting av behandler: $hprNummer")
                throw UnauthorizedException("Norsk helsenett returnerte Unauthorized ved oppslag av HPR-nummer $hprNummer")
            }
            else -> {
                log.error("Noe gikk galt ved henting av behandler fra syfohelsenettproxy $callId")
                throw RuntimeException("En ukjent feil oppsto ved ved henting av behandler $callId")
            }
        }
    }
}

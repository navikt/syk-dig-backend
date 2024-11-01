package no.nav.sykdig.digitalisering.helsenett

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.sykmelding.Behandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate

@Component
class HelsenettClient(
    @Value("\${helsenett.url}") private val url: String,
    private val helsenettClientRestTemplate: RestTemplate,
) {
    val log = applog()

    fun finnBehandler(
        authorization: String,
        callId: String,
        hprNummer: String,
    ): ResponseEntity<Behandler> {
        log.info("Henter behandler fra syfohelsenettproxy for callId {}", callId)

        val url = "$url/api/v2/behandlerMedHprNummer"
        val headers =
            HttpHeaders().apply {
                accept = listOf(MediaType.APPLICATION_JSON)
                set("Authorization", "$authorization")
                set("hprNummer", padHpr(hprNummer)!!)
            }
        val entity = HttpEntity<Any>(headers)

        return try {
            val response =
                helsenettClientRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Behandler::class.java,
                )

            when (response.statusCode) {
                HttpStatus.OK -> {
                    log.info("Hentet behandler for callId {}", callId)
                    ResponseEntity.ok(response.body ?: throw RuntimeException("Behandler response body is null"))
                }
                HttpStatus.INTERNAL_SERVER_ERROR -> {
                    log.error("Syfohelsenettproxy svarte med feilmelding for callId {}", callId)
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
                HttpStatus.NOT_FOUND -> {
                    log.warn("Fant ikke behandler for HprNummer $hprNummer for callId $callId")
                    ResponseEntity.status(HttpStatus.NOT_FOUND).build()
                }
                HttpStatus.UNAUTHORIZED -> {
                    log.warn("Norsk helsenett returnerte Unauthorized for henting av behandler: $hprNummer")
                    ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                }
                else -> {
                    log.error("Noe gikk galt ved henting av behandler fra syfohelsenettproxy $callId")
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
                }
            }
        } catch (e: HttpClientErrorException) {
            log.error("Feil ved henting av behandler fra syfohelsenettproxy: ${e.message}", e)
            throw RuntimeException("Error fetching Behandler: ${e.message}", e)
        }
    }

    fun padHpr(hprnummer: String?): String? {
        if (hprnummer?.length != null && hprnummer.length < 9) {
            return hprnummer.padStart(9, '0')
        }
        return hprnummer
    }
}

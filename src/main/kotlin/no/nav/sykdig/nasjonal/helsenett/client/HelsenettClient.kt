package no.nav.sykdig.nasjonal.helsenett.client

import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.SykmelderNotFoundException
import no.nav.sykdig.nasjonal.helsenett.Behandler
import no.nav.sykdig.shared.securelog
import org.springframework.beans.factory.annotation.Value

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class HelsenettClient(
    @Value("\${helsenett.url}") private val helsenettUrl: String,
    private val helsenettM2mWebClient: WebClient,
) {
    val log = applog()
    val securelog = securelog()

    fun getBehandler(
        hprNummer: String,
        callId: String,
    ): Behandler {
        log.info("Henter behandler fra syfohelsenettproxy for callId {}", callId)

        return try {
            helsenettM2mWebClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("$helsenettUrl/api/v2/behandlerMedHprNummer")
                        .build()
                }
                .headers { headers ->
                    headers["Nav-CallId"] = callId
                    headers["hprNummer"] = hprNummer
                }
                .retrieve()
                .onStatus({ status -> status == HttpStatus.NOT_FOUND }) {
                    Mono.error(SykmelderNotFoundException("Behandler ikke funnet for hprNummer $hprNummer"))
                }
                .bodyToMono(Behandler::class.java)
                .block() ?: throw SykmelderNotFoundException("Behandler ikke funnet for hprNummer $hprNummer")
        } catch (e: WebClientResponseException) {
            if (e.statusCode == HttpStatus.NOT_FOUND) {
                throw SykmelderNotFoundException("Behandler ikke funnet for hprNummer $hprNummer")
            }
            throw e
        }
    }
}



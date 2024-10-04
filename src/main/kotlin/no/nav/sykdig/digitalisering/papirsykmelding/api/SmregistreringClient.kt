package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@Component
class SmregistreringClient(
    @Value("\${smregistrering.url}") private val url: String,
    private val smregisteringRestTemplate: RestTemplate,
) {
    val log = applog()
    val secureLog = securelog()

    @Retryable
    fun postSmregistreringRequest(
        token: String,
        oppgaveId: String,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        try {
            smregisteringRestTemplate.exchange(
                "$url/oppgave/$oppgaveId/avvis",
                HttpMethod.POST,
                HttpEntity(null, headers),
                String::class.java,
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("smregistering_backend $oppgaveId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode " +
                        "${e.statusCode.value()} fra Oppgave ved ferdigstilling: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode " +
                    "${e.statusCode.value()} fra Oppgave ved ferdigstilling: ${e.message}",
                e,
            )
            throw e
        }
    }
}

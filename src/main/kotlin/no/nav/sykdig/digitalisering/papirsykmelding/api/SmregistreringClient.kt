package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
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
        typeRequest: String,
        enhet: String,
        avvisSykmeldingReason: String?,
    ): String {
        log.info("Inne i postSmregistreringRequest")
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", enhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        return try {
            val response =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/oppgave/$oppgaveId/$typeRequest",
                    HttpMethod.POST,
                    HttpEntity(null, headers),
                    String::class.java,
                )
            log.info("postSmregistreringRequest response mottatt: ${response.body}")
            response.body ?: "ingen respons fra server"
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

    @Retryable
    fun getSmregistreringRequest(
        token: String,
        oppgaveId: String,
    ): PapirManuellOppgave {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        log.info("gjør kall til smreg på oppgaveId $oppgaveId header $headers")
        return try {
            val response =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/oppgave/$oppgaveId",
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    PapirManuellOppgave::class.java,
                )
            response.body ?: throw NoOppgaveException("Fant ikke oppgaver med id $oppgaveId")
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

    @Retryable
    fun getPasientNavnRequest(
        token: String,
        fnr: String,
    ): PasientNavn {
        val headers = HttpHeaders()
        headers.set("X-Pasient-Fnr", fnr)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        return try {
            val response =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/pasient",
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    PasientNavn::class.java,
                )
            response.body ?: throw NoOppgaveException("Fant ikke person")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                throw IkkeTilgangException("Veileder har ikke tilgang til pasient")
            } else {
                log.error(
                    "HttpClientErrorException for pasient med responskode " +
                        "${e.statusCode.value()} og message: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for pasient med responskode " +
                    "${e.statusCode.value()} og message: ${e.message}",
                e,
            )
            throw e
        }
    }
}


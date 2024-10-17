package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate

@Component
class SmregistreringClient(
    @Value("\${smregistrering.url}") private val url: String,
    val smregisteringRestTemplate: RestTemplate,
) {
    val log = applog()

    @Retryable
    fun postSmregistreringRequest(
        token: String,
        oppgaveId: String,
        typeRequest: String,
        navEnhet: String,
        avvisSykmeldingReason: String?,
    ) {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        try {
            smregisteringRestTemplate.exchange(
                "$url/api/v1/oppgave/$oppgaveId/$typeRequest",
                HttpMethod.POST,
                HttpEntity(AvvisSykmeldingRequest(avvisSykmeldingReason), headers),
                String::class.java,
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode " +
                        "${e.statusCode.value()} fra Oppgave ved avvis: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode " +
                    "${e.statusCode.value()} fra Oppgave ved avvis: ${e.message}",
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
        try {
            val response =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/oppgave/$oppgaveId",
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    PapirManuellOppgave::class.java,
                )
            return response.body ?: throw NoOppgaveException("Fant ikke oppgaver med id $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("smregistering_backend $oppgaveId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode " +
                        "${e.statusCode.value()} fra Oppgave ved henting: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode " +
                    "${e.statusCode.value()} fra Oppgave ved henting: ${e.message}",
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
        try {
            val response =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/pasient",
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    PasientNavn::class.java,
                )
            return response.body ?: throw NoOppgaveException("Fant ikke person")
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

    @Retryable
    fun getSykmelderRequest(
        token: String,
        hprNummer: String,
    ): Sykmelder {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        try {
            val response =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/sykmelder/$hprNummer",
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    Sykmelder::class.java,
                )
            return response.body ?: throw NoOppgaveException("Fant ikke sykmelder")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                throw IkkeTilgangException("Veileder har ikke tilgang til sykmelder")
            } else {
                log.error(
                    "HttpClientErrorException for sykmelder med responskode " +
                        "${e.statusCode.value()} og message: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for sykmelder med responskode " +
                    "${e.statusCode.value()} og message: ${e.message}",
                e,
            )
            throw e
        }
    }

    @Retryable
    fun postSendOppgaveRequest(
        token: String,
        oppgaveId: String,
        navEnhet: String,
        papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String>? {
        val headers = HttpHeaders()
        headers.set("X-Nav-Enhet", navEnhet)
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        try {
            smregisteringRestTemplate.exchange(
                "$url/api/v1/oppgave/$oppgaveId/send",
                HttpMethod.POST,
                HttpEntity(papirSykmelding, headers),
                String::class.java,
            )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else if (e.statusCode.value() == 400) {
                log.error(
                    "Send oppgave has RuleHits: ${e.statusCode.value()} og statusText: ${e.statusText}",
                    e,
                )
                return ResponseEntity.badRequest().body(e.statusText)
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode " +
                        "${e.statusCode.value()} fra Oppgave ved sending: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode " +
                    "${e.statusCode.value()} fra Oppgave ved sending: ${e.message}",
                e,
            )
            throw e
        }
        return null
    }

    @Retryable
    fun getFerdigstiltSykmeldingRequest(
        token: String,
        sykmeldingId: String,
    ): PapirManuellOppgave {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        try {
            val response =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/sykmelding/$sykmeldingId/ferdigstilt",
                    HttpMethod.GET,
                    HttpEntity<String>(headers),
                    PapirManuellOppgave::class.java,
                )
            return response.body ?: throw NoOppgaveException("Fant ikke ferdigstilt sykmelding")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                throw IkkeTilgangException("Veileder har ikke tilgang til sykmelder")
            } else {
                log.error(
                    "HttpClientErrorException for ferdigstilt sykmelding med responskode " + "${e.statusCode.value()} og message: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for ferdigsilt sykmelding med responskode " +
                    "${e.statusCode.value()} og message: ${e.message}",
                e,
            )
            throw e
        }
    }

    @Retryable
    fun postOppgaveTilGosysRequest(
        token: String,
        oppgaveId: String,
    ): ResponseEntity<HttpStatusCode> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(token)
        return try {
            val res =
                smregisteringRestTemplate.exchange(
                    "$url/api/v1/oppgave/$oppgaveId/tilgosys",
                    HttpMethod.POST,
                    HttpEntity(null, headers),
                    HttpStatusCode::class.java,
                )
            if (res.statusCode.is4xxClientError) {
                log.error("client error ved sending av oppgave til Gosys med kode ${res.statusCode}")
                ResponseEntity(res.statusCode)
            } else if (res.statusCode.is5xxServerError) {
                log.error("server error ved sending av oppgave til Gosys med kode ${res.statusCode}")
                ResponseEntity(res.statusCode)
            } else {
                log.info("oppgave sendt til Gosys med følgende responskode ${res.statusCode}")
                res
            }
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode " +
                        "${e.statusCode.value()} ved forsøk på å sende oppgave til gosys: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode " +
                    "${e.statusCode.value()} ved forsøk på å sende oppgave til gosys: ${e.message}",
                e,
            )
            throw e
        }
    }
}

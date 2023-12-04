package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.getFristForFerdigstillingAvOppgave
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.logger
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
import java.time.LocalDate
import java.util.UUID

private const val OPPGAVETYPE = "JFR"

private const val PRIORITET_NORM = "NORM"

private const val BEHANDLES_AV_APPLIKASJON = "SMD"

private const val TILDELT_ENHETSNR = "0393"

private const val OPPRETTET_AV_ENHETSNR = "9999"

@Component
class OppgaveClient(
    @Value("\${oppgave.url}") private val url: String,
    private val oppgaveRestTemplate: RestTemplate,
) {
    val log = logger()

    fun ferdigstillOppgave(oppgaveId: String, sykmeldingId: String) {
        val oppgave = getOppgave(oppgaveId, sykmeldingId)
        if (oppgave.status == Oppgavestatus.FERDIGSTILT || oppgave.status == Oppgavestatus.FEILREGISTRERT) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
        } else {
            ferdigstillOppgave(oppgaveId, sykmeldingId, oppgave.versjon)
            log.info("Ferdigstilt oppgave med id $oppgaveId i Oppgave")
        }
    }

    @Retryable
    fun getOppgave(oppgaveId: String, sykmeldingId: String): GetOppgaveResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        try {
            val response = oppgaveRestTemplate.exchange(
                "$url/$oppgaveId",
                HttpMethod.GET,
                HttpEntity<Any>(headers),
                GetOppgaveResponse::class.java,
            )
            return response.body ?: throw RuntimeException("Fant ikke oppgave med id $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til oppgaveId $oppgaveId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error("HttpServerErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    private fun ferdigstillOppgave(oppgaveId: String, sykmeldingId: String, oppgaveVersjon: Int) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        val body = PatchFerdigStillOppgaveRequest(
            versjon = oppgaveVersjon,
            status = Oppgavestatus.FERDIGSTILT,
            id = oppgaveId.toInt(),
        )
        try {
            oppgaveRestTemplate.exchange(
                "$url/$oppgaveId",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                String::class.java,
            )
            log.info("Ferdigstilt oppgave $oppgaveId for sykmelding $sykmeldingId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å ferdigstille oppgaveId $oppgaveId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} fra Oppgave ved ferdigstilling: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} fra Oppgave ved ferdigstilling: ${e.message}",
                e,
            )
            throw e
        }
    }

    @Retryable
    fun oppdaterOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        oppgaveVersjon: Int,
        oppgaveStatus: Oppgavestatus,
        oppgaveBehandlesAvApplikasjon: String,
        oppgaveTilordnetRessurs: String,
        beskrivelse: String?,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        val body = PatchToGosysOppgaveRequest(
            versjon = oppgaveVersjon,
            status = oppgaveStatus,
            id = oppgaveId.toInt(),
            behandlesAvApplikasjon = oppgaveBehandlesAvApplikasjon,
            tilordnetRessurs = oppgaveTilordnetRessurs,
            beskrivelse = beskrivelse,
        )

        try {
            oppgaveRestTemplate.exchange(
                "$url/$oppgaveId",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                String::class.java,
            )
            log.info("OppdaterOppgave oppgave $oppgaveId for sykmelding $sykmeldingId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder $oppgaveTilordnetRessurs har ikke tilgang til å oppdaterOppgave oppgaveId $oppgaveId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} fra Oppgave ved oppdaterOppgave: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} fra Oppgave ved oppdaterOppgave: ${e.message}",
                e,
            )
            throw e
        }
    }

    fun opprettOppgave(
        journalpostId: String,
    ): GetOppgaveResponse {
        val headers = HttpHeaders()
        val xCorrelationId = UUID.randomUUID().toString()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = xCorrelationId
        try {
            return oppgaveRestTemplate.exchange(
                url,
                HttpMethod.POST,
                HttpEntity(
                    CreateOppgaveRequest(
                        journalpostId = journalpostId,
                        tema = TEMA_SYKMELDING,
                        oppgavetype = OPPGAVETYPE,
                        prioritet = PRIORITET_NORM,
                        opprettetAvEnhetsnr = OPPRETTET_AV_ENHETSNR,
                        aktivDato = LocalDate.now(),
                        behandlesAvApplikasjon = BEHANDLES_AV_APPLIKASJON,
                        fristFerdigstillelse = getFristForFerdigstillingAvOppgave(LocalDate.now()),
                        tildeltEnhetsnr = TILDELT_ENHETSNR,
                    ),
                    headers,
                ),
                GetOppgaveResponse::class.java,
            ).body!!
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å opprette oppgaveId $journalpostId med correlation id $xCorrelationId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til å opprette oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $journalpostId med responskode ${e.statusCode.value()} fra Oppgave ved createOppgave med correlation id $xCorrelationId: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $journalpostId med responskode ${e.statusCode.value()} fra Oppgave ved createOppgave med correlation id $xCorrelationId: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Kunne ikke opprette oppgave med på journalpostId $journalpostId ved createOppgave med correlation id $xCorrelationId: ${e.message}",
                e,
            )
            throw e
        }
    }
}

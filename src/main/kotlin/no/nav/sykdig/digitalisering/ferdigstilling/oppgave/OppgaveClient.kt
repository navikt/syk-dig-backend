package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
import no.nav.sykdig.digitalisering.getFristForFerdigstillingAvOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.FerdigstillRegistrering
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import java.time.LocalDate
import java.util.*

private const val OPPGAVETYPE = "JFR"

private const val PRIORITET_NORM = "NORM"

private const val BEHANDLES_AV_APPLIKASJON = "SMD"

private const val TILDELT_ENHETSNR = "0393"

private const val OPPRETTET_AV_ENHETSNR = "9999"

private const val BEHANDLINGS_TYPE_UTLAND = "ae0106"

@Component
class OppgaveClient(
    @Value("\${oppgave.url}") private val url: String,
    private val oppgaveRestTemplate: RestTemplate,
    private val oppgaveM2mRestTemplate: RestTemplate,
) {
    val log = applog()
    val secureLog = securelog()

    fun ferdigstillOppgave(
        oppgaveId: String,
        sykmeldingId: String,
    ) {
        val oppgave = getOppgave(oppgaveId, sykmeldingId)
        if (oppgave.status == OppgaveStatus.FERDIGSTILT || oppgave.status == OppgaveStatus.FEILREGISTRERT) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
        } else {
            ferdigstillOppgave(oppgaveId, sykmeldingId, oppgave.versjon)
            log.info("Ferdigstilt oppgave med id $oppgaveId i Oppgave")
        }
    }

    fun ferdigstillNasjonalOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta
    ) {
        val oppgave = getNasjonalOppgave(oppgaveId, sykmeldingId)
        if (oppgave.status == OppgaveStatus.FERDIGSTILT.name || oppgave.status == OppgaveStatus.FEILREGISTRERT.name) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            return
        }
        val ferdigstillOppgave = PatchFerdigstillNasjonalOppgaveRequest(
            id = oppgaveId.toInt(),
            versjon = oppgave.versjon ?: throw RuntimeException(
            "Fant ikke versjon for oppgave ${oppgave.id}, sykmeldingId ${ferdigstillRegistrering.sykmeldingId}"
        ),
            status = OppgaveStatus.FERDIGSTILT,
            tilordnetRessurs = ferdigstillRegistrering.veileder.veilederIdent,
            tildeltEnhetsnr = ferdigstillRegistrering.navEnhet,
            mappeId = null,
            beskrivelse = oppgave.beskrivelse,
        )
        log.info(
            "Ferdigstiller oppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveId),
            StructuredArguments.fields(loggingMeta),
        )
        ferdigstillNasjonalOppgave(oppgaveId, sykmeldingId, ferdigstillOppgave)
        log.info("Ferdigstilt oppgave med id $oppgaveId i Oppgave")
    }

    @Retryable
    fun getOppgaveM2m(
        oppgaveId: String,
        sykmeldingId: String,
    ): GetOppgaveResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        try {
            val response =
                oppgaveM2mRestTemplate.exchange(
                    "$url/$oppgaveId",
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    GetOppgaveResponse::class.java,
                )
            return response.body ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("syk-dig-backend har ikke tilgang til oppgaveId $oppgaveId: ${e.message}")
                throw IkkeTilgangException("syk-dig-backend har ikke tilgang til oppgave")
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
        } catch (e: Exception) {
            log.error("Other Exception fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    fun getOppgave(
        oppgaveId: String,
        sykmeldingId: String,
    ): GetOppgaveResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId
        try {
            val response =
                oppgaveRestTemplate.exchange(
                    "$url/$oppgaveId",
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    GetOppgaveResponse::class.java,
                )
            return response.body ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til oppgaveId $oppgaveId: ${e.message} med httpStatus ${e.statusCode.value()}")
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave med id: $oppgaveId")
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
        } catch (e: Exception) {
            log.error("Other Exception fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    fun getNasjonalOppgave(
        oppgaveId: String,
        sykmeldingId: String,
    ): NasjonalOppgaveResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId
        try {
            val response =
                oppgaveM2mRestTemplate.exchange(
                    "$url/$oppgaveId",
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    NasjonalOppgaveResponse::class.java,
                )
            return response.body ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til oppgaveId $oppgaveId: ${e.message} med httpStatus ${e.statusCode.value()}")
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave med id: $oppgaveId")
            } else {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message} med httpStatus ${e.statusCode.value()}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error("HttpServerErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            log.error("Other Exception fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    fun getOppgaver(
        journalpostId: String,
        journalpost: SafJournalpost,
    ): List<AllOppgaveResponse> {
        val headers = HttpHeaders()
        val urlWithParams = urlWithParams(journalpostId, journalpost)
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = UUID.randomUUID().toString()

        log.info("Kaller oppgaveRestTemplate.exchange med URL: $urlWithParams og journalpostId: $journalpostId")
        try {
            val response =
                oppgaveRestTemplate.exchange(
                    urlWithParams,
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    AllOppgaveResponses::class.java,
                )
            log.info("Mottok respons for journalpostId $journalpostId med antall oppgaver: ${response.body?.oppgaver?.size ?: "ingen"}")
            return response.body?.oppgaver ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $journalpostId")
        } catch (e: HttpClientErrorException) {
            log.error(
                "HttpClientErrorException med responskode ${e.statusCode.value()} fra journalpostId $journalpostId. Detaljer: ${e.message}",
                e,
            )
            throw e
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.statusCode.value()} fra journalpostId $journalpostId. Detaljer: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error("Generell Exception blir kastet ved henting av oppgaver på journalpostId $journalpostId. Detaljer: ${e.message}", e)
            throw e
        }
    }

    private fun urlWithParams(
        journalpostId: String,
        journalpost: SafJournalpost,
    ): String {
        if (journalpost.bruker == null) throw NoOppgaveException("ingen oppgaver på journalpostId $journalpost fordi bruker er null")
        return "$url?journalpostId=$journalpostId&statuskategori=AAPEN"
    }

    @Retryable
    private fun ferdigstillOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        oppgaveVersjon: Int,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        val body =
            PatchFerdigStillOppgaveRequest(
                versjon = oppgaveVersjon,
                status = OppgaveStatus.FERDIGSTILT,
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
    private fun ferdigstillNasjonalOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        nasjonalFerdigstillOppgave: PatchFerdigstillNasjonalOppgaveRequest
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        try {
            val response = oppgaveRestTemplate.exchange(
                "$url/$oppgaveId",
                HttpMethod.PATCH,
                HttpEntity(nasjonalFerdigstillOppgave, headers),
                String::class.java,
            )
            log.info("Ferdigstilt nasjonal oppgave $oppgaveId for sykmelding $sykmeldingId")
            return response
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til å ferdigstille oppgaveId $oppgaveId: ${e.message}")
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
    fun ferdigstillJournalføringsoppgave(
        oppgaveId: Int,
        oppgaveVersjon: Int,
        journalpostId: String,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = UUID.randomUUID().toString()

        val body =
            PatchFerdigStillOppgaveRequest(
                versjon = oppgaveVersjon,
                status = OppgaveStatus.FERDIGSTILT,
                id = oppgaveId,
            )
        try {
            oppgaveRestTemplate.exchange(
                "$url/$oppgaveId",
                HttpMethod.PATCH,
                HttpEntity(body, headers),
                String::class.java,
            )
            log.info(
                "Ferdigstilt journalføringsopoppgave {} {}",
                kv("journalpostId", journalpostId),
                kv("oppgaveId", oppgaveId),
            )
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId og journalpostId $journalpostId med responskode " +
                    "${e.statusCode.value()} fra Oppgave ved ferdigstilling: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error("Exception. Fra journalpost: ${e.message}", e)
            throw e
        }
    }

    fun oppdaterOppgaveM2m(
        oppdaterOppgaveRequest: OppdaterOppgaveRequest,
        sykmeldingId: String,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId
        val oppgaveId = oppdaterOppgaveRequest.id

        try {
            oppgaveM2mRestTemplate.exchange(
                "$url/$oppgaveId",
                HttpMethod.PATCH,
                HttpEntity(oppdaterOppgaveRequest, headers),
                String::class.java,
            )
            log.info("OppdaterOppgave oppgave $oppgaveId for sykmelding $sykmeldingId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn(
                    "Syk-dig-backend har ikke tilgang til å " +
                        "oppdaterOppgave oppgaveId $oppgaveId: ${e.message}",
                )
                throw IkkeTilgangException("Syk-dig har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} " +
                        "fra Oppgave ved oppdaterOppgave: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} " +
                    "fra Oppgave ved oppdaterOppgave: ${e.message}",
                e,
            )
            throw e
        }
    }

    @Retryable
    fun oppdaterGosysOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        oppgaveVersjon: Int,
        oppgaveStatus: OppgaveStatus,
        oppgaveBehandlesAvApplikasjon: String,
        oppgaveTilordnetRessurs: String,
        beskrivelse: String?,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        val body =
            PatchToGosysOppgaveRequest(
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
                log.warn(
                    "Veileder $oppgaveTilordnetRessurs har ikke tilgang til å " +
                        "oppdaterOppgave oppgaveId $oppgaveId: ${e.message}",
                )
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} " +
                        "fra Oppgave ved oppdaterOppgave: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} " +
                    "fra Oppgave ved oppdaterOppgave: ${e.message}",
                e,
            )
            throw e
        }
    }

    fun opprettOppgave(
        journalpostId: String,
        aktoerId: String,
    ): GetOppgaveResponse {
        val headers = HttpHeaders()
        val xCorrelationId = UUID.randomUUID().toString()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = xCorrelationId
        try {
            val result =
                oppgaveRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    HttpEntity(
                        CreateOppgaveRequest(
                            aktoerId = aktoerId,
                            journalpostId = journalpostId,
                            tema = TEMA_SYKMELDING,
                            oppgavetype = OPPGAVETYPE,
                            prioritet = PRIORITET_NORM,
                            opprettetAvEnhetsnr = OPPRETTET_AV_ENHETSNR,
                            aktivDato = LocalDate.now(),
                            behandlesAvApplikasjon = BEHANDLES_AV_APPLIKASJON,
                            behandlingstype = BEHANDLINGS_TYPE_UTLAND,
                            fristFerdigstillelse = getFristForFerdigstillingAvOppgave(LocalDate.now()),
                            tildeltEnhetsnr = TILDELT_ENHETSNR,
                        ),
                        headers,
                    ),
                    GetOppgaveResponse::class.java,
                )
            secureLog.info("OpprettOppgave: $journalpostId: ${objectMapper.writeValueAsString(result.body)}, aktørId: $aktoerId")
            val oppgave = result.body!!
            log.info("OpprettOppgave fra journalpostId: $journalpostId  med oppgaveId: ${oppgave.id}")
            return oppgave
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn(
                    "Veileder har ikke tilgang til å opprette oppgaveId $journalpostId " +
                        "med correlation id $xCorrelationId: ${e.message}",
                )
                throw IkkeTilgangException("Veileder har ikke tilgang til å opprette oppgave")
            } else {
                log.error(
                    "HttpClientErrorException for oppgaveId $journalpostId med responskode " +
                        "${e.statusCode.value()} fra Oppgave ved createOppgave med correlation id $xCorrelationId: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $journalpostId med responskode" +
                    " ${e.statusCode.value()} fra Oppgave ved createOppgave med correlation id $xCorrelationId: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Kunne ikke opprette oppgave med på journalpostId $journalpostId " +
                    "ved createOppgave med correlation id $xCorrelationId: ${e.message}",
                e,
            )
            throw e
        }
    }
}

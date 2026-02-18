package no.nav.sykdig.gosys

import java.time.LocalDate
import java.util.*
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.gosys.models.*
import no.nav.sykdig.gosys.models.OpprettNasjonalOppgave
import no.nav.sykdig.nasjonal.models.FerdigstillRegistrering
import no.nav.sykdig.saf.graphql.SafJournalpost
import no.nav.sykdig.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.IkkeTilgangException
import no.nav.sykdig.shared.exceptions.NoOppgaveException
import no.nav.sykdig.shared.objectMapper
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.utenlandsk.services.getFristForFerdigstillingAvOppgave
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

    fun ferdigstillOppgave(oppgaveId: String, sykmeldingId: String, endretAvEnhetsnr: String?) {
        val oppgave = getOppgave(oppgaveId, sykmeldingId)
        if (
            oppgave.status == OppgaveStatus.FERDIGSTILT ||
                oppgave.status == OppgaveStatus.FEILREGISTRERT
        ) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
        } else {
            ferdigstillOppgave(oppgaveId, sykmeldingId, oppgave.versjon, endretAvEnhetsnr)
            log.info("Ferdigstilt oppgave med id $oppgaveId i Oppgave")
        }
    }

    fun ferdigstillNasjonalOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta,
        beskrivelse: String?,
        endretAvEnhetsnr: String?,
    ) {
        val oppgave = getNasjonalOppgave(oppgaveId, sykmeldingId)
        if (
            oppgave.status == OppgaveStatus.FERDIGSTILT.name ||
                oppgave.status == OppgaveStatus.FEILREGISTRERT.name
        ) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            return
        }
        val ferdigstillOppgave =
            PatchFerdigstillNasjonalOppgaveRequest(
                id = oppgaveId.toInt(),
                versjon =
                    oppgave.versjon
                        ?: throw RuntimeException(
                            "Fant ikke versjon for oppgave ${oppgave.id}, sykmeldingId ${ferdigstillRegistrering.sykmeldingId}"
                        ),
                status = OppgaveStatus.FERDIGSTILT,
                tilordnetRessurs = ferdigstillRegistrering.veileder.veilederIdent,
                tildeltEnhetsnr = ferdigstillRegistrering.navEnhet,
                mappeId = null,
                beskrivelse =
                    if (beskrivelse?.isNotBlank() == true) beskrivelse else oppgave.beskrivelse,
                endretAvEnhetsnr = endretAvEnhetsnr,
            )
        log.info(
            "Ferdigstiller nasjonal oppgave med {}, {}",
            StructuredArguments.keyValue("oppgaveId", oppgaveId),
            StructuredArguments.fields(loggingMeta),
        )
        ferdigstillNasjonalOppgave(oppgaveId, sykmeldingId, ferdigstillOppgave)
        log.info("Ferdigstilt oppgave med id $oppgaveId i Oppgave")
    }

    @Retryable
    fun getOppgaveM2m(oppgaveId: String, sykmeldingId: String): GetOppgaveResponse {
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
            return response.body
                ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("syk-dig-backend har ikke tilgang til oppgaveId $oppgaveId: ${e.message}")
                throw IkkeTilgangException("syk-dig-backend har ikke tilgang til oppgave")
            } else {
                log.warn(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error("Other Exception fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    fun getOppgave(oppgaveId: String, sykmeldingId: String): GetOppgaveResponse {
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
            return response.body
                ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn(
                    "Veileder har ikke tilgang til oppgaveId $oppgaveId: ${e.message} med httpStatus ${e.statusCode.value()}"
                )
                throw IkkeTilgangException(
                    "Veileder har ikke tilgang til oppgave med id: $oppgaveId"
                )
            } else {
                log.warn(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error("Other Exception fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    fun getNasjonalOppgave(oppgaveId: String, sykmeldingId: String): NasjonalOppgaveResponse {
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
            return response.body
                ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $oppgaveId")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn(
                    "Veileder har ikke tilgang til oppgaveId $oppgaveId: ${e.message} med httpStatus ${e.statusCode.value()}"
                )
                throw IkkeTilgangException(
                    "Veileder har ikke tilgang til oppgave med id: $oppgaveId"
                )
            } else {
                log.warn(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message} med httpStatus ${e.statusCode.value()}",
                    e,
                )
                throw e
            }
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error("Other Exception fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    private fun checkOppgavetype(response: List<AllOppgaveResponse>) {
        val (gyldige, ugyldige) =
            response.partition {
                it.oppgavetype in enumValues<AllOppgaveType>().map { enum -> enum.name }
            }
        gyldige.forEach { log.info("Gyldig oppgaveType: $it") }
        ugyldige.forEach { log.warn("Ugyldig oppgaveType mottatt: $it") }
    }

    @Retryable
    fun getOppgaver(journalpostId: String, journalpost: SafJournalpost): List<AllOppgaveResponse> {
        val headers = HttpHeaders()
        val urlWithParams = urlWithParams(journalpostId, journalpost)
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = UUID.randomUUID().toString()

        log.info(
            "Kaller oppgaveRestTemplate.exchange med URL: $urlWithParams og journalpostId: $journalpostId"
        )
        try {
            val response =
                oppgaveRestTemplate.exchange(
                    urlWithParams,
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    AllOppgaveResponses::class.java,
                )
            log.info(
                "Mottok respons for journalpostId $journalpostId med antall oppgaver: ${response.body?.oppgaver?.size ?: "ingen"}"
            )
            return response.body?.oppgaver?.also { checkOppgavetype(it) }
                ?: throw NoOppgaveException("Fant ikke oppgaver på journalpostId $journalpostId")
        } catch (e: HttpClientErrorException) {
            log.warn(
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
            log.error(
                "Generell Exception blir kastet ved henting av oppgaver på journalpostId $journalpostId. Detaljer: ${e.message}",
                e,
            )
            throw e
        }
    }

    private fun urlWithParams(journalpostId: String, journalpost: SafJournalpost): String {
        if (journalpost.bruker == null)
            throw NoOppgaveException(
                "ingen oppgaver på journalpostId $journalpost fordi bruker er null"
            )
        return "$url?journalpostId=$journalpostId&statuskategori=AAPEN"
    }

    @Retryable
    private fun ferdigstillOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        oppgaveVersjon: Int,
        endretAvEnhetsnr: String?,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        val body =
            PatchFerdigStillOppgaveRequest(
                versjon = oppgaveVersjon,
                status = OppgaveStatus.FERDIGSTILT,
                id = oppgaveId.toInt(),
                endretAvEnhetsnr = endretAvEnhetsnr,
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
                log.warn(
                    "Veileder har ikke tilgang til å ferdigstille oppgaveId $oppgaveId: ${e.message}"
                )
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.warn(
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
        nasjonalFerdigstillOppgave: PatchFerdigstillNasjonalOppgaveRequest,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = sykmeldingId

        try {
            val response =
                oppgaveM2mRestTemplate.exchange(
                    "$url/$oppgaveId",
                    HttpMethod.PATCH,
                    HttpEntity(nasjonalFerdigstillOppgave, headers),
                    String::class.java,
                )
            log.info("Ferdigstilt nasjonal oppgave $oppgaveId for sykmelding $sykmeldingId")
            return response
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn(
                    "Veileder har ikke tilgang til å ferdigstille oppgaveId $oppgaveId: ${e.message} med httpStatus ${e.statusCode.value()}"
                )
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
            } else {
                log.warn(
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
        endretAvEnhetsnr: String?,
    ) {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = UUID.randomUUID().toString()

        val body =
            PatchFerdigStillOppgaveRequest(
                versjon = oppgaveVersjon,
                status = OppgaveStatus.FERDIGSTILT,
                id = oppgaveId,
                endretAvEnhetsnr = endretAvEnhetsnr,
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

    fun oppdaterOppgaveM2m(oppdaterOppgaveRequest: OppdaterOppgaveRequest, sykmeldingId: String) {
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
                        "oppdaterOppgave oppgaveId $oppgaveId: ${e.message}"
                )
                throw IkkeTilgangException("Syk-dig har ikke tilgang til oppgave")
            } else {
                log.warn(
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
        endretAvEnhetsnr: String?,
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
                endretAvEnhetsnr = endretAvEnhetsnr,
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
            handleClientError(e, oppgaveTilordnetRessurs, oppgaveId)
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} " +
                    "fra Oppgave ved oppdaterOppgave: ${e.message}",
                e,
            )
            throw e
        }
    }

    fun oppdaterNasjonalGosysOppgave(
        oppdatertOppgave: NasjonalOppgaveResponse,
        sykmeldingId: String,
        oppgaveId: String,
        veileder: String?,
    ): NasjonalOppgaveResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = oppgaveId

        try {
            val result =
                oppgaveM2mRestTemplate.exchange(
                    "$url/${oppdatertOppgave.id}",
                    HttpMethod.PUT,
                    HttpEntity(oppdatertOppgave, headers),
                    NasjonalOppgaveResponse::class.java,
                )
            log.info("OppdaterNasjonalOppgave oppgave $oppgaveId for sykmelding $sykmeldingId")
            return result.body
                ?: throw NoOppgaveException("Fant ikke oppgave for oppgaveId $oppgaveId")
        } catch (e: HttpClientErrorException) {
            handleClientError(e, veileder, oppgaveId)
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} " +
                    "fra Oppgave ved oppdaterOppgave: ${e.message}",
                e,
            )
            throw e
        }
    }

    fun opprettOppgave(journalpostId: String, aktoerId: String): GetOppgaveResponse {
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
                            fristFerdigstillelse =
                                getFristForFerdigstillingAvOppgave(LocalDate.now()),
                            tildeltEnhetsnr = TILDELT_ENHETSNR,
                        ),
                        headers,
                    ),
                    GetOppgaveResponse::class.java,
                )
            secureLog.info(
                "OpprettOppgave: $journalpostId: ${objectMapper.writeValueAsString(result.body)}, aktørId: $aktoerId"
            )
            val oppgave = result.body!!
            log.info(
                "OpprettOppgave fra journalpostId: $journalpostId  med oppgaveId: ${oppgave.id}"
            )
            return oppgave
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn(
                    "Veileder har ikke tilgang til å opprette oppgaveId $journalpostId " +
                        "med correlation id $xCorrelationId: ${e.message}"
                )
                throw IkkeTilgangException("Veileder har ikke tilgang til å opprette oppgave")
            } else {
                log.warn(
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

    fun opprettNasjonalOppgave(
        opprettNasjonalOppgave: OpprettNasjonalOppgave,
        msgId: String,
    ): NasjonalOppgaveResponse {
        log.info(
            "Oppretter oppgave for msgId {}, journalpostId {}",
            msgId,
            opprettNasjonalOppgave.journalpostId,
        )
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["X-Correlation-ID"] = msgId

        try {
            val result =
                oppgaveM2mRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    HttpEntity(opprettNasjonalOppgave, headers),
                    NasjonalOppgaveResponse::class.java,
                )
            secureLog.info(
                "OpprettNasjonalOppgave med journalpostId: ${opprettNasjonalOppgave.journalpostId}: ${objectMapper.writeValueAsString(result.body)}, aktørId: ${opprettNasjonalOppgave.aktoerId}"
            )
            val oppgave = result.body!!
            log.info(
                "OpprettNasjonalOppgave fra journalpostId: ${opprettNasjonalOppgave.journalpostId}  med oppgaveId: ${oppgave.id}"
            )
            return oppgave
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException for oppgaveId ${opprettNasjonalOppgave.journalpostId} med responskode" +
                    " ${e.statusCode.value()} fra Oppgave ved createOppgave med correlation id $msgId: ${e.message}",
                e,
            )
            throw e
        } catch (e: Exception) {
            log.error(
                "Kunne ikke opprette oppgave med på journalpostId ${opprettNasjonalOppgave.journalpostId} +",
                "ved createOppgave med correlation id $msgId: ${e.message}",
                e,
            )
            throw e
        }
    }

    private fun handleClientError(
        e: HttpClientErrorException,
        veileder: String?,
        oppgaveId: String,
    ): Nothing {
        if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
            log.warn(
                "Veileder $veileder har ikke tilgang til å " +
                    "oppdaterOppgave oppgaveId $oppgaveId: ${e.message}"
            )
            throw IkkeTilgangException("Veileder har ikke tilgang til oppgave")
        } else {
            log.warn(
                "HttpClientErrorException for oppgaveId $oppgaveId med responskode ${e.statusCode.value()} " +
                    "fra Oppgave ved oppdaterOppgave: ${e.message}",
                e,
            )
            throw e
        }
    }
}

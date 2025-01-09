package no.nav.sykdig.oppgave

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.IkkeTilgangException
import no.nav.sykdig.shared.exceptions.NoOppgaveException
import no.nav.sykdig.utenlandsk.services.getFristForFerdigstillingAvOppgave
import no.nav.sykdig.nasjonal.models.FerdigstillRegistrering
import no.nav.sykdig.saf.graphql.SafJournalpost
import no.nav.sykdig.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.shared.objectMapper
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.oppgave.models.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
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
    private val oppgaveWebClient: WebClient,
) {
    val log = applog()
    val secureLog = securelog()

    suspend fun ferdigstillOppgave(
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

    suspend fun ferdigstillNasjonalOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta,
        beskrivelse: String?
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
            beskrivelse = if (beskrivelse?.isNotBlank() == true) beskrivelse else oppgave.beskrivelse,
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
    suspend fun getOppgaveM2m(
        oppgaveId: String,
        sykmeldingId: String,
    ): GetOppgaveResponse {
        log.info("Fetching oppgave with ID: $oppgaveId")

        return try {
            oppgaveWebClient.get()
                .uri("$url/$oppgaveId")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = sykmeldingId
                }
                .retrieve()
                .onStatus({ status -> status.value() == 401 || status.value() == 403 }) { response ->
                    log.warn("Access denied for oppgaveId $oppgaveId: ${response.statusCode()}")
                    Mono.error(IkkeTilgangException("syk-dig-backend har ikke tilgang til oppgave"))
                }
                .onStatus({ status -> !status.is2xxSuccessful }) { response ->
                    log.error("Unexpected error from Oppgave service: ${response.statusCode()}")
                    response.bodyToMono(String::class.java).flatMap { errorBody ->
                        Mono.error(Exception("Error from Oppgave: $errorBody"))
                    }
                }
                .bodyToMono(GetOppgaveResponse::class.java)
                .awaitSingle()
        } catch (e: WebClientResponseException) {
            handleWebClientResponseException(e, oppgaveId)
        } catch (e: Exception) {
            log.error("Unexpected exception while fetching oppgave: ${e.message}", e)
            throw e
        }
    }

    private fun handleWebClientResponseException(
        e: WebClientResponseException,
        oppgaveId: String,
        veileder: String?
    ): GetOppgaveResponse {
        if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
            log.warn("syk-dig-backend har ikke tilgang til oppgaveId $oppgaveId: ${e.message}")
            throw IkkeTilgangException("syk-dig-backend har ikke tilgang til oppgave")
        } else {
            log.error(
                "WebClientResponseException with status code ${e.statusCode.value()} from Oppgave: ${e.message}",
                e,
            )
            throw e
        }
    }

    @Retryable
    suspend fun getOppgave(
        oppgaveId: String,
        sykmeldingId: String,
    ): GetOppgaveResponse {
        log.info("Fetching oppgave with ID: $oppgaveId for sykmelding: $sykmeldingId")

        return try {
            oppgaveWebClient.get()
                .uri("$url/$oppgaveId")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = sykmeldingId
                }
                .retrieve()
                .onStatus({ status -> status.value() == 401 || status.value() == 403 }) { response ->
                    log.warn(
                        "Veileder does not have access to oppgaveId $oppgaveId: ${response.statusCode()}"
                    )
                    Mono.error(IkkeTilgangException("Veileder har ikke tilgang til oppgave med id: $oppgaveId"))
                }
                .onStatus({ status -> !status.is2xxSuccessful }) { response ->
                    log.error("Unexpected error while fetching oppgaveId $oppgaveId: ${response.statusCode()}")
                    response.bodyToMono(String::class.java).flatMap { errorBody ->
                        Mono.error(Exception("Error fetching oppgave: $errorBody"))
                    }
                }
                .bodyToMono(GetOppgaveResponse::class.java)
                .awaitSingle()
        } catch (e: WebClientResponseException) {
            handleWebClientResponseException(e, oppgaveId)
        } catch (e: Exception) {
            log.error("Unexpected exception while fetching oppgaveId $oppgaveId: ${e.message}", e)
            throw e
        }
    }

    private fun handleWebClientResponseException(
        e: WebClientResponseException,
        oppgaveId: String,
    ): GetOppgaveResponse {
        if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
            log.warn(
                "Veileder does not have access to oppgaveId $oppgaveId: ${e.message} with httpStatus ${e.statusCode.value()}"
            )
            throw IkkeTilgangException("Veileder har ikke tilgang til oppgave med id: $oppgaveId")
        } else {
            log.error(
                "WebClientResponseException with status code ${e.statusCode.value()} for oppgaveId $oppgaveId: ${e.message}",
                e,
            )
            throw e
        }
    }

    @Retryable
    suspend fun getNasjonalOppgave(
        oppgaveId: String,
        sykmeldingId: String,
    ): NasjonalOppgaveResponse {
        try {
            return oppgaveWebClient.get()
                .uri("$url/$oppgaveId")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = sykmeldingId
                }
                .retrieve()
                .onStatus({ status -> status.value() == 401 || status.value() == 403 }) {
                    log.warn("Veileder har ikke tilgang til oppgaveId $oppgaveId: ${it.statusCode()}")
                    Mono.error(IkkeTilgangException("Veileder har ikke tilgang til oppgave med id: $oppgaveId"))
                }
                .onStatus({ status -> !status.is2xxSuccessful }) {
                    log.error("HttpClientErrorException med responskode ${it.statusCode()} fra Oppgave")
                    Mono.error(Exception("Unexpected error when fetching oppgave"))
                }
                .bodyToMono(NasjonalOppgaveResponse::class.java)
                .awaitSingle()
        } catch (e: WebClientResponseException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn(
                    "Veileder har ikke tilgang til oppgaveId $oppgaveId: ${e.message} med httpStatus ${e.statusCode.value()}"
                )
                throw IkkeTilgangException("Veileder har ikke tilgang til oppgave med id: $oppgaveId")
            }
            log.error(
                "HttpClientErrorException med responskode ${e.statusCode.value()} fra Oppgave: ${e.message}",
                e
            )
            throw e
        } catch (e: Exception) {
            log.error("Other Exception fra Oppgave: ${e.message}", e)
            throw e
        }
    }

    private fun checkOppgavetype(response: List<AllOppgaveResponse>) {
        val (gyldige, ugyldige) = response.partition { it.oppgavetype in enumValues<AllOppgaveType>().map { enum -> enum.name} }
        gyldige.forEach { log.info("Gyldig oppgaveType: $it") }
        ugyldige.forEach { log.warn("Ugyldig oppgaveType mottatt: $it") }
    }

    @Retryable
    suspend fun getOppgaver(
        journalpostId: String,
        journalpost: SafJournalpost,
    ): List<AllOppgaveResponse> {
        val urlWithParams = urlWithParams(journalpostId, journalpost)

        try {
            val response = oppgaveWebClient.get()
                .uri(urlWithParams)
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = UUID.randomUUID().toString()
                }
                .retrieve()
                .onStatus({ status -> !status.is2xxSuccessful }) {
                    log.error("HttpClientErrorException med responskode ${it.statusCode()} fra journalpostId $journalpostId")
                    Mono.error(Exception("Unexpected error when fetching oppgaver"))
                }
                .bodyToMono(AllOppgaveResponses::class.java)
                .awaitSingle()

            log.info("Mottok respons for journalpostId $journalpostId med antall oppgaver: ${response.oppgaver.size}")
            return response.oppgaver.also { checkOppgavetype(it) }
        } catch (e: WebClientResponseException) {
            log.error(
                "WebClientResponseException med responskode ${e.statusCode.value()} fra journalpostId $journalpostId. Detaljer: ${e.message}",
                e
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
    suspend fun ferdigstillOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        oppgaveVersjon: Int,
    ) {
        val body = PatchFerdigStillOppgaveRequest(
            versjon = oppgaveVersjon,
            status = OppgaveStatus.FERDIGSTILT,
            id = oppgaveId.toInt(),
        )

        try {
            oppgaveWebClient.patch()
                .uri("$url/$oppgaveId")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = sykmeldingId
                }
                .bodyValue(body)
                .retrieve()
                .onStatus({ status -> status.value() == 401 || status.value() == 403 }) {
                    log.warn("Veileder har ikke tilgang til å ferdigstille oppgaveId $oppgaveId: ${it.statusCode()}")
                    Mono.error(IkkeTilgangException("Veileder har ikke tilgang til oppgave"))
                }
                .onStatus({ status -> !status.is2xxSuccessful }) {
                    log.error("HttpClientErrorException for oppgaveId $oppgaveId: ${it.statusCode()}")
                    Mono.error(Exception("Error when completing oppgave"))
                }
                .bodyToMono(String::class.java)
                .awaitSingle()

            log.info("Ferdigstilt oppgave $oppgaveId for sykmelding $sykmeldingId")
        } catch (e: WebClientResponseException) {
            log.error(
                "WebClientResponseException for oppgaveId $oppgaveId: ${e.message} with status ${e.statusCode}",
                e
            )
            throw e
        } catch (e: Exception) {
            log.error("Exception when completing oppgaveId $oppgaveId: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    suspend fun ferdigstillNasjonalOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        nasjonalFerdigstillOppgave: PatchFerdigstillNasjonalOppgaveRequest
    ): ResponseEntity<String> {
        try {
            val response = oppgaveWebClient.patch()
                .uri("$url/$oppgaveId")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = sykmeldingId
                }
                .bodyValue(nasjonalFerdigstillOppgave)
                .retrieve()
                .onStatus({ status -> status.value() == 401 || status.value() == 403 }) {
                    log.warn("Veileder har ikke tilgang til å ferdigstille oppgaveId $oppgaveId: ${it.statusCode()}")
                    Mono.error(IkkeTilgangException("Veileder har ikke tilgang til oppgave"))
                }
                .onStatus({ status -> !status.is2xxSuccessful }) {
                    log.error("HttpClientErrorException for oppgaveId $oppgaveId: ${it.statusCode()}")
                    Mono.error(Exception("Error when completing nasjonal oppgave"))
                }
                .toEntity(String::class.java)
                .awaitSingle()

            log.info("Ferdigstilt nasjonal oppgave $oppgaveId for sykmelding $sykmeldingId")
            return response
        } catch (e: WebClientResponseException) {
            log.error(
                "WebClientResponseException for oppgaveId $oppgaveId: ${e.message} with status ${e.statusCode}",
                e
            )
            throw e
        } catch (e: Exception) {
            log.error("Exception when completing nasjonal oppgaveId $oppgaveId: ${e.message}", e)
            throw e
        }
    }

    @Retryable
    suspend fun ferdigstillJournalføringsoppgave(
        oppgaveId: Int,
        oppgaveVersjon: Int,
        journalpostId: String,
    ) {
        val body = PatchFerdigStillOppgaveRequest(
            versjon = oppgaveVersjon,
            status = OppgaveStatus.FERDIGSTILT,
            id = oppgaveId,
        )

        try {
            oppgaveWebClient.patch()
                .uri("$url/$oppgaveId")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = UUID.randomUUID().toString()
                }
                .bodyValue(body)
                .retrieve()
                .onStatus({ status -> !status.is2xxSuccessful }) {
                    log.error("Error when completing journalføringsoppgave with oppgaveId $oppgaveId and journalpostId $journalpostId")
                    Mono.error(Exception("Error completing journalføringsoppgave"))
                }
                .bodyToMono(String::class.java)
                .awaitSingle()

            log.info(
                "Ferdigstilt journalføringsoppgave {} {}",
                kv("journalpostId", journalpostId),
                kv("oppgaveId", oppgaveId),
            )
        } catch (e: WebClientResponseException) {
            log.error(
                "WebClientResponseException for oppgaveId $oppgaveId and journalpostId $journalpostId: ${e.message} with status ${e.statusCode}",
                e
            )
            throw e
        } catch (e: Exception) {
            log.error("Exception when completing journalføringsoppgave: ${e.message}", e)
            throw e
        }
    }

    suspend fun oppdaterOppgaveM2m(
        oppdaterOppgaveRequest: OppdaterOppgaveRequest,
        sykmeldingId: String,
    ) {
        val oppgaveId = oppdaterOppgaveRequest.id
        val body = oppdaterOppgaveRequest

        try {
            oppgaveWebClient.patch()
                .uri("$url/$oppgaveId")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = sykmeldingId
                }
                .bodyValue(body)
                .retrieve()
                .onStatus({ status -> !status.is2xxSuccessful }) {
                    log.error(
                        "Error updating oppgave $oppgaveId for sykmelding $sykmeldingId with status: ${it.statusCode()}",
                        it
                    )
                    Mono.error(Exception("Error updating oppgave"))
                }
                .bodyToMono(String::class.java)
                .awaitSingle()

            log.info("OppdaterOppgave oppgave $oppgaveId for sykmelding $sykmeldingId")
        } catch (e: WebClientResponseException) {
            if (e.statusCode == HttpStatus.UNAUTHORIZED || e.statusCode == HttpStatus.FORBIDDEN) {
                log.warn("Syk-dig-backend has no access to update oppgaveId $oppgaveId: ${e.message}")
                throw IkkeTilgangException("Syk-dig has no access to oppgave")
            } else {
                log.error(
                    "WebClientResponseException for oppgaveId $oppgaveId: ${e.message} with status ${e.statusCode}",
                    e
                )
                throw e
            }
        } catch (e: Exception) {
            log.error("Exception when updating oppgaveId $oppgaveId: ${e.message}", e)
            throw e
        }
    }


    @Retryable
    suspend fun oppdaterGosysOppgave(
        oppgaveId: String,
        sykmeldingId: String,
        oppgaveVersjon: Int,
        oppgaveStatus: OppgaveStatus,
        oppgaveBehandlesAvApplikasjon: String,
        oppgaveTilordnetRessurs: String,
        beskrivelse: String?,
    ) {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            this["X-Correlation-ID"] = sykmeldingId
        }

        val body = PatchToGosysOppgaveRequest(
            versjon = oppgaveVersjon,
            status = oppgaveStatus,
            id = oppgaveId.toInt(),
            behandlesAvApplikasjon = oppgaveBehandlesAvApplikasjon,
            tilordnetRessurs = oppgaveTilordnetRessurs,
            beskrivelse = beskrivelse,
        )

        try {
            oppgaveWebClient.patch()
                .uri("$url/$oppgaveId")
                .headers { it.addAll(headers) }
                .bodyValue(body)
                .retrieve()
                .onStatus({ status -> !status.is2xxSuccessful }) { response ->
                    Mono.error(Exception("Error response: ${response.statusCode()}"))
                }
                .bodyToMono(String::class.java)
                .awaitSingle()

            log.info("OppdaterOppgave oppgave $oppgaveId for sykmelding $sykmeldingId")
        } catch (e: WebClientResponseException) {
            if (e.statusCode == HttpStatus.UNAUTHORIZED || e.statusCode == HttpStatus.FORBIDDEN) {
                log.warn("Veileder $oppgaveTilordnetRessurs has no access to update oppgaveId $oppgaveId: ${e.message}")
                throw IkkeTilgangException("Veileder has no access to oppgave")
            } else {
                log.error(
                    "WebClientResponseException for oppgaveId $oppgaveId: ${e.message} with status ${e.statusCode}",
                    e
                )
                throw e
            }
        } catch (e: Exception) {
            log.error("Exception when updating oppgaveId $oppgaveId: ${e.message}", e)
            throw e
        }
    }

    suspend fun opprettOppgave(
        journalpostId: String,
        aktoerId: String,
    ): GetOppgaveResponse {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            this["X-Correlation-ID"] = UUID.randomUUID().toString()
        }

        val createRequest = CreateOppgaveRequest(
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
        )

        try {
            val response = oppgaveWebClient.post()
                .uri(url)
                .headers { it.addAll(headers) }
                .bodyValue(createRequest)
                .retrieve()
                .onStatus({ status -> !status.is2xxSuccessful }) { response ->
                    Mono.error(Exception("Error response: ${response.statusCode()}"))
                }
                .bodyToMono(GetOppgaveResponse::class.java)
                .awaitSingle()

            secureLog.info("OpprettOppgave: $journalpostId: ${objectMapper.writeValueAsString(response)}, aktørId: $aktoerId")
            log.info("OpprettOppgave fra journalpostId: $journalpostId med oppgaveId: ${response.id}")
            return response
        } catch (e: WebClientResponseException) {
            val xCorrelationId = headers["X-Correlation-ID"]?.first() ?: "N/A"
            if (e.statusCode == HttpStatus.UNAUTHORIZED || e.statusCode == HttpStatus.FORBIDDEN) {
                log.warn("Veileder har ikke tilgang til å opprette oppgaveId $journalpostId med correlation id $xCorrelationId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til å opprette oppgave")
            } else {
                log.error(
                    "WebClientResponseException for oppgaveId $journalpostId med responskode ${e.statusCode} " +
                            "fra Oppgave ved createOppgave med correlation id $xCorrelationId: ${e.message}",
                    e,
                )
                throw e
            }
        } catch (e: Exception) {
            log.error("Kunne ikke opprette oppgave med på journalpostId $journalpostId ved createOppgave: ${e.message}", e)
            throw e
        }
    }

    suspend fun oppdaterNasjonalGosysOppgave(
        oppdatertOppgave: NasjonalOppgaveResponse,
        sykmeldingId: String,
        oppgaveId: String,
        veileder: String,
    ) {
        log.info("Updating oppgave with ID: $oppgaveId for sykmelding: $sykmeldingId")

        try {
            oppgaveWebClient.put()
                .uri("$url/${oppdatertOppgave.id}")
                .headers { headers ->
                    headers.contentType = MediaType.APPLICATION_JSON
                    headers["X-Correlation-ID"] = oppgaveId
                }
                .bodyValue(oppdatertOppgave)
                .retrieve()
                .onStatus({ status -> status.value() == 401 || status.value() == 403 }) { response ->
                    log.warn(
                        "Veileder $veileder does not have access to update oppgaveId $oppgaveId: ${response.statusCode()}"
                    )
                    Mono.error(IkkeTilgangException("Veileder har ikke tilgang til oppgave"))
                }
                .onStatus({ status -> !status.is2xxSuccessful }) { response ->
                    log.error(
                        "Unexpected error when updating oppgaveId $oppgaveId: ${response.statusCode()}"
                    )
                    response.bodyToMono(String::class.java).flatMap { errorBody ->
                        Mono.error(Exception("Error updating oppgave: $errorBody"))
                    }
                }
                .bodyToMono(String::class.java) // Consuming response as String
                .awaitSingleOrNull()

            log.info("Oppgave $oppgaveId successfully updated for sykmelding $sykmeldingId")
        } catch (e: WebClientResponseException) {
            handleWebClientResponseException(e, oppgaveId, veileder)
        } catch (e: Exception) {
            log.error("Unexpected exception while updating oppgaveId $oppgaveId: ${e.message}", e)
            throw e
        }
    }
}

package no.nav.sykdig.saf

import kotlinx.coroutines.reactor.awaitSingleOrNull
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.api.DocumentController.ErrorTypes
import no.nav.sykdig.utenlandsk.api.DocumentController.PdfLoadingState
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class SafClient(
    @Value("\${saf.url}") private val safUrl: String,
    private val safWebClient: WebClient,
) {
    val log = applog()

    @Retryable
    suspend fun getPdfFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        callId: String,
    ): PdfLoadingState {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            accept = listOf(MediaType.APPLICATION_PDF)
            set("Nav-Callid", callId)
            set("Nav-Consumer-Id", "syk-dig-backend")
        }

        try {
            val validJournalpostId = journalpostId.toLongOrNull()
            val validDokumentInfoId = dokumentInfoId.toLongOrNull()

            if (validJournalpostId == null || validDokumentInfoId == null) {
                log.error("Ugyldig journalpostId: $journalpostId eller dokumentInfoId: $dokumentInfoId er på ugyldig format")
                return PdfLoadingState.Bad(ErrorTypes.INVALID_FORMAT)
            }

            val response = safWebClient.get()
                .uri("$safUrl/rest/hentdokument/$validJournalpostId/$validDokumentInfoId/ARKIV")
                .headers { it.addAll(headers) }
                .retrieve()
                .onStatus({ it != HttpStatus.OK }) { response ->
                    Mono.error(HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Feil ved henting av PDF fra SAF"))
                }
                .bodyToMono(ByteArray::class.java)
                .awaitSingleOrNull()

            return if (response?.isEmpty() == true) {
                log.error("Tomt svar fra SAF for journalpostId $journalpostId")
                PdfLoadingState.Bad(ErrorTypes.EMPTY_RESPONSE)
            } else {
                PdfLoadingState.Good(response!!)
            }

        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til journalpostId $journalpostId: ${e.message}")
                return PdfLoadingState.Bad(ErrorTypes.SAKSBEHANDLER_IKKE_TILGANG)
            } else {
                log.error("HttpClientErrorException med responskode ${e.statusCode.value()} fra SAF: ${e.message}", e)
            }
            return PdfLoadingState.Bad(ErrorTypes.SAF_CLIENT_ERROR)
        } catch (e: HttpServerErrorException) {
            log.error("HttpServerErrorException med responskode ${e.statusCode.value()} fra SAF: ${e.message}", e)
            return PdfLoadingState.Bad(ErrorTypes.SAF_CLIENT_ERROR)
        }
    }
}


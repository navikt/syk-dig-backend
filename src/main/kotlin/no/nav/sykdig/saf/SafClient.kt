package no.nav.sykdig.saf

import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.api.DocumentController.ErrorTypes
import no.nav.sykdig.utenlandsk.api.DocumentController.PdfLoadingState
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
class SafClient(
    @Value("\${saf.url}") private val safUrl: String,
    private val safRestTemplate: RestTemplate,
) {
    val log = applog()

    @Retryable
    fun getPdfFraSaf(
        journalpostId: String,
        dokumentInfoId: String,
        callId: String,
    ): PdfLoadingState {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers.accept = listOf(MediaType.APPLICATION_PDF)
        headers["Nav-Callid"] = callId
        headers["Nav-Consumer-Id"] = "syk-dig-backend"

        try {
            val validJournalpostId =
                try {
                    journalpostId.toLong()
                } catch (exception: Exception) {
                    log.error("Ugyldig dokumentInfoId: $dokumentInfoId er på ugyldig format")
                    return PdfLoadingState.Bad(ErrorTypes.INVALID_FORMAT)
                }

            val validDokumentInfoId =
                try {
                    dokumentInfoId.toLong()
                } catch (exception: Exception) {
                    log.error("Ugyldig dokumentInfoId: $dokumentInfoId er på ugyldig format")
                    return PdfLoadingState.Bad(ErrorTypes.INVALID_FORMAT)
                }

            if (journalpostId.toLongOrNull() != null && dokumentInfoId.toLongOrNull() != null) {
                val response =
                    safRestTemplate.exchange(
                        "$safUrl/rest/hentdokument/$validJournalpostId/$validDokumentInfoId/ARKIV",
                        HttpMethod.GET,
                        HttpEntity<Any>(headers),
                        ByteArray::class.java,
                    )

                if (response.body?.isEmpty() == true) {
                    log.error("Tomt svar fra SAF for journalpostId $journalpostId")
                    return PdfLoadingState.Bad(ErrorTypes.EMPTY_RESPONSE)
                } else {
                    return PdfLoadingState.Good(response.body!!)
                }
            } else {
                log.error(
                    "Ugyldig journalpostId: $journalpostId eller dokumentInfoId: $dokumentInfoId er på ugyldig format"
                )
                return PdfLoadingState.Bad(ErrorTypes.INVALID_FORMAT)
            }
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til journalpostId $journalpostId: ${e.message}")
                return PdfLoadingState.Bad(ErrorTypes.SAKSBEHANDLER_IKKE_TILGANG)
            } else {
                log.error(
                    "HttpClientErrorException med responskode ${e.statusCode.value()} fra SAF: ${e.message}",
                    e,
                )
            }
            return PdfLoadingState.Bad(ErrorTypes.SAF_CLIENT_ERROR)
        } catch (e: HttpServerErrorException) {
            log.error(
                "HttpServerErrorException med responskode ${e.statusCode.value()} fra SAF: ${e.message}",
                e,
            )
            return PdfLoadingState.Bad(ErrorTypes.SAF_CLIENT_ERROR)
        }
    }
}

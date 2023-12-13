package no.nav.sykdig.digitalisering.saf

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
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
    ): ByteArray {
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
                    throw RuntimeException("Ugyldig journalpostId: $journalpostId er på ugyldigformat")
                }

            val validDokumentInfoId =
                try {
                    dokumentInfoId.toLong()
                } catch (exception: Exception) {
                    throw RuntimeException("Ugyldig dokumentInfoId: $dokumentInfoId er på ugyldigformat")
                }

            if (journalpostId.toLongOrNull() != null && dokumentInfoId.toLongOrNull() != null) {
                val response =
                    safRestTemplate.exchange(
                        "$safUrl/rest/hentdokument/$validJournalpostId/$validDokumentInfoId/ARKIV",
                        HttpMethod.GET,
                        HttpEntity<Any>(headers),
                        ByteArray::class.java,
                    )
                return response.body ?: throw RuntimeException("Tomt svar fra SAF for journalpostId $journalpostId")
            } else {
                throw RuntimeException("Ugyldig journalpostId: $journalpostId eller dokumentInfoId: $dokumentInfoId er på ugyldigformat")
            }
        } catch (e: HttpClientErrorException) {
            if (e.statusCode.value() == 401 || e.statusCode.value() == 403) {
                log.warn("Veileder har ikke tilgang til journalpostId $journalpostId: ${e.message}")
                throw IkkeTilgangException("Veileder har ikke tilgang til journalpost")
            } else {
                log.error("HttpClientErrorException med responskode ${e.statusCode.value()} fra SAF: ${e.message}", e)
            }
            throw RuntimeException("HttpClientErrorException fra SAF")
        } catch (e: HttpServerErrorException) {
            log.error("HttpServerErrorException med responskode ${e.statusCode.value()} fra SAF: ${e.message}", e)
            throw RuntimeException("HttpServerErrorException fra SAF")
        }
    }
}

package no.nav.sykdig.utenlandsk.api

import java.util.*
import no.nav.sykdig.saf.SafClient
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.services.SykDigOppgaveService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class DocumentController(
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val safClient: SafClient,
) {
    val log = applog()

    @GetMapping(
        "/api/document/journalpost/{journalpostId}/{dokumentInfoId}",
        produces = [MediaType.APPLICATION_PDF_VALUE],
    )
    @PreAuthorize("@oppgaveSecurityService.hasAccessToJournalpostId(#journalpostId)")
    @ResponseBody
    fun getJournalpostDocument(
        @PathVariable journalpostId: String,
        @PathVariable dokumentInfoId: String,
    ): ResponseEntity<Any> {
        return getPdfResult(
            safClient.getPdfFraSaf(journalpostId, dokumentInfoId, UUID.randomUUID().toString())
        )
    }

    @GetMapping(
        "/api/document/{oppgaveId}/{dokumentInfoId}",
        produces = [MediaType.APPLICATION_PDF_VALUE],
    )
    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @ResponseBody
    fun getOppgaveDocument(
        @PathVariable oppgaveId: String,
        @PathVariable dokumentInfoId: String,
    ): ResponseEntity<Any> {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)

        if (dokumentInfoId == "primary") {
            if (oppgave.dokumentInfoId == null) {
                log.error("oppgave: $oppgaveId mangler dokumentInfoId")
                return ResponseEntity.badRequest()
                    .body(toHtml("oppgave: $oppgaveId mangler dokumentInfoId"))
            }
            return getPdfResult(
                safClient.getPdfFraSaf(
                    dokumentInfoId = oppgave.dokumentInfoId,
                    journalpostId = oppgave.journalpostId,
                    callId = oppgave.sykmeldingId.toString(),
                )
            )
        }

        val dokumentInfoIdInDokumenter =
            oppgave.dokumenter.firstOrNull { it.dokumentInfoId == dokumentInfoId }

        if (dokumentInfoIdInDokumenter == null && oppgave.dokumentInfoId != dokumentInfoId) {
            log.error("$dokumentInfoId er ikke en del av oppgave $oppgaveId")
            return ResponseEntity.badRequest()
                .header("Content-Type", "text/html")
                .body(toHtml("dokumentInfoId: $dokumentInfoId er ikke gyldig for oppgaven"))
        }

        try {
            return getPdfResult(
                safClient.getPdfFraSaf(
                    dokumentInfoId = dokumentInfoId,
                    journalpostId = oppgave.journalpostId,
                    callId = oppgave.sykmeldingId.toString(),
                )
            )
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av pdf for oppgave med id $oppgaveId")
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "text/html")
                .body(toHtml("Noe gikk galt ved henting av pdf for oppgave med id $oppgaveId"))
        }
    }

    enum class ErrorTypes {
        PDF_NOT_OK,
        PDF_GOOD_BUT_SAF_BAD,
        SAF_CLIENT_ERROR,
        EMPTY_RESPONSE,
        INVALID_FORMAT,
        SAKSBEHANDLER_IKKE_TILGANG,
    }

    sealed class PdfLoadingState {
        data class Good(val value: ByteArray) : PdfLoadingState()

        data class Bad(val value: ErrorTypes) : PdfLoadingState()
    }
}

fun getPdfResult(pdfResult: DocumentController.PdfLoadingState): ResponseEntity<Any> {
    return when (pdfResult) {
        is DocumentController.PdfLoadingState.Good -> {
            ResponseEntity.ok().header("Content-Type", "application/pdf").body(pdfResult.value)
        }

        is DocumentController.PdfLoadingState.Bad -> {
            when (pdfResult.value) {
                DocumentController.ErrorTypes.PDF_NOT_OK ->
                    ResponseEntity.ok()
                        .header("Content-Type", "text/html")
                        .body("<html><body><h1>PDF is in bad shape!</h1></body></html>")

                DocumentController.ErrorTypes.PDF_GOOD_BUT_SAF_BAD ->
                    ResponseEntity.ok()
                        .header("Content-Type", "text/html")
                        .body("<html><body><h1>Problem med SAF forespørsel</h1></body></html>")

                DocumentController.ErrorTypes.EMPTY_RESPONSE ->
                    ResponseEntity.ok()
                        .header("Content-Type", "text/html")
                        .body("<html><body><h1>Tomt svar frå SAF</h1></body></html>")

                DocumentController.ErrorTypes.INVALID_FORMAT ->
                    ResponseEntity.ok()
                        .header("Content-Type", "text/html")
                        .body(
                            "<html><body><h1>JournalpostId eller DocumentId er på feil format.</h1></body></html>"
                        )

                DocumentController.ErrorTypes.SAKSBEHANDLER_IKKE_TILGANG ->
                    ResponseEntity.ok()
                        .header("Content-Type", "text/html")
                        .body(
                            "<html><body><h1>Veileder har ikke tilgang til journalpost.</h1></body></html>"
                        )

                DocumentController.ErrorTypes.SAF_CLIENT_ERROR ->
                    ResponseEntity.ok()
                        .header("Content-Type", "text/html")
                        .body(
                            "<html><body><h1>SAF client feil har oppstått. Sjekk loggen</h1></body></html>"
                        )
            }
        }
    }
}

fun toHtml(input: String): String {
    return "<html><body><h1>$input</h1></body></html>"
}

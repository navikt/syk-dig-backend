package no.nav.sykdig.digitalisering.api

import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.applog
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.saf.SafClient
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PdfController(
    private val oppgaveRepository: OppgaveRepository,
    private val safClient: SafClient,
) {
    val log = applog()

    @Deprecated("Bruk /api/document/{oppgaveId}/{dokumentInfoId} i stedet")
    @GetMapping("/api/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @ResponseBody
    fun getPdf(
        @RequestParam oppgaveId: String,
        dfe: DataFetchingEnvironment,
    ): ResponseEntity<Any> {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave != null) {
            try {
                return getPdfResult(
                    safClient.getPdfFraSaf(
                        journalpostId = oppgave.journalpostId,
                        dokumentInfoId = oppgave.dokumentInfoId ?: "",
                        callId = oppgave.sykmeldingId.toString(),
                    ),
                )
            } catch (e: Exception) {
                log.error("Noe gikk galt ved henting av pdf for oppgave med id $oppgaveId")
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "text/html")
                    .body(toHtml("Noe gikk galt ved henting av pdf"))
            }
        } else {
            log.warn("Fant ikke oppgave med id $oppgaveId ved henting av pdf")
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .header("Content-Type", "text/html")
                .body(toHtml("Fant ikke oppgave med id $oppgaveId ved henting av pdf"))
        }
    }
}

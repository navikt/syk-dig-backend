package no.nav.sykdig.digitalisering.api

import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.saf.SafClient
import no.nav.sykdig.logger
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

@RestController
class PdfController(
    private val oppgaveRepository: OppgaveRepository,
    private val safClient: SafClient,
) {
    val log = logger()

    @Deprecated("Bruk /api/document/{oppgaveId}/{dokumentInfoId} i stedet")
    @GetMapping("/api/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @ResponseBody
    fun getPdf(@RequestParam oppgaveId: String): ByteArray {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave != null) {
            try {
                return safClient.hentPdfFraSaf(
                    journalpostId = oppgave.journalpostId,
                    dokumentInfoId = oppgave.dokumentInfoId ?: "",
                    sykmeldingId = oppgave.sykmeldingId.toString(),
                )
            } catch (e: Exception) {
                log.error("Noe gikk galt ved henting av pdf for oppgave med id $oppgaveId")
                throw HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Noe gikk galt ved henting av pdf")
            }
        } else {
            log.warn("Fant ikke oppgave med id $oppgaveId ved henting av pdf")
            throw HttpClientErrorException(HttpStatus.NOT_FOUND, "Fant ikke oppgave")
        }
    }
}

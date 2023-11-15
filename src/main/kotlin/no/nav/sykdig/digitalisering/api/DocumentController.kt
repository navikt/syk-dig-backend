package no.nav.sykdig.digitalisering.api

import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.saf.SafClient
import no.nav.sykdig.logger
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.util.UUID

@RestController
class DocumentController(
    private val oppgaveRepository: OppgaveRepository,
    private val safClient: SafClient,
) {
    val log = logger()

    @GetMapping("/api/document/journalpost/{journalpostId}/{dokumentInfoId}", produces = [MediaType.APPLICATION_PDF_VALUE])
    @PreAuthorize("@oppgaveSecurityService.hasAccessToJournalpost(#journalpostId)")
    @ResponseBody
    fun getJournalpostDocument(
        @PathVariable journalpostId: String,
        @PathVariable dokumentInfoId: String,
    ): ByteArray {
        return safClient.getPdfFraSaf(journalpostId, dokumentInfoId, UUID.randomUUID().toString())
    }

    @GetMapping("/api/document/{oppgaveId}/{dokumentInfoId}", produces = [MediaType.APPLICATION_PDF_VALUE])
    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @ResponseBody
    fun getOppgaveDocument(
        @PathVariable oppgaveId: String,
        @PathVariable dokumentInfoId: String,
    ): ByteArray {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId)

        if (oppgave != null) {
            if (
                dokumentInfoId == "primary"
            ) {
                if (oppgave.dokumentInfoId == null) {
                    log.error("$oppgaveId mangler dokumentInfoId")
                    throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "$oppgaveId mangler dokumentInfoId")
                }

                return safClient.getPdfFraSaf(
                    dokumentInfoId = oppgave.dokumentInfoId,
                    journalpostId = oppgave.journalpostId,
                    callId = oppgave.sykmeldingId.toString(),
                )
            }

            val dokumentInfoIdInDokumenter = oppgave.dokumenter.firstOrNull { it.dokumentInfoId == dokumentInfoId }

            if (dokumentInfoIdInDokumenter == null && oppgave.dokumentInfoId != dokumentInfoId) {
                log.error("$dokumentInfoId er ikke en del av oppgave $oppgaveId")
                throw HttpClientErrorException(HttpStatus.BAD_REQUEST, "dokumentInfoId er ikke gyldig for oppgaven")
            }

            try {
                return safClient.getPdfFraSaf(
                    dokumentInfoId = dokumentInfoId,
                    journalpostId = oppgave.journalpostId,
                    callId = oppgave.sykmeldingId.toString(),
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

package no.nav.sykdig.digitalisering.sykmelding.api

import no.nav.sykdig.digitalisering.saf.SafClient
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody

class JournalpostController(
    private val safClient: SafClient
) {
    @GetMapping("/api/journalpost/{journalpostId}", produces = [MediaType.APPLICATION_JSON_VALUE])

    @ResponseBody
    fun getJournalpostById(
        @PathVariable oppgaveId: String
    ) {


    }
}


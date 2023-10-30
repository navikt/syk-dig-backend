package no.nav.sykdig.digitalisering.sykmelding.api

import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class JournalpostController(
    private val safGraphQlClient: SafJournalpostGraphQlClient,
) {
    @GetMapping("/api/journalpost/{journalpostId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun getJournalpostById(
        @PathVariable journalpostId: String,
    ): SafJournalpost {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)
        return SafJournalpost(
            journalpostId,
            journalpost.journalpost?.journalstatus?.name ?: "MANGLER_STATUS",
        )
    }
}

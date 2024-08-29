package no.nav.syfo.oppgave.saf.client

import no.nav.syfo.oppgave.saf.client.model.FindJournalpostRequest
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostVariables
import no.nav.sykdig.applog
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

class SafGraphQlClient(
    private val webClient: WebClient,
    private val basePath: String,
    private val graphQlQuery: String,
) {
    val logger = applog()

    fun findJournalpost(
        journalpostId: String,
        token: String,
        sporingsId: String,
    ): FindJournalpostResponse? {
        val findJournalpostRequest =
            FindJournalpostRequest(
                query = graphQlQuery,
                variables = FindJournalpostVariables(journalpostId = journalpostId),
            )

        return try {
            webClient.post()
                .uri(basePath)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .header("X-Correlation-ID", sporingsId)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(findJournalpostRequest)
                .retrieve()
                .bodyToMono<FindJournalpostResponse>()
                .block()
        } catch (e: Exception) {
            logger.error(
                "Noe gikk galt ved kall til SAF, journalpostId $journalpostId, sporingsId $sporingsId",
                e,
            )
            throw e
        }
    }
}

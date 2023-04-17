package no.nav.sykdig.digitalisering.saf

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.digitalisering.saf.graphql.SAF_DOCUMENT_QUERY
import no.nav.sykdig.digitalisering.saf.graphql.SafDocument
import no.nav.sykdig.digitalisering.saf.graphql.SafDocumentQuery
import no.nav.sykdig.logger
import org.springframework.stereotype.Component

@Component
class SafDocumentClient(
    private val safDocumentGraphQlClient: CustomGraphQLClient,
) {
    val log = logger()
    fun getDokumenter(journalpostId: String): List<SafDocument> {
        try {
            val response = safDocumentGraphQlClient.executeQuery(SAF_DOCUMENT_QUERY, mapOf("id" to journalpostId))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }

            val safResponse = response.dataAsObject(SafDocumentQuery::class.java)
            return safResponse.journalpost?.dokumenter ?: emptyList()
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF", e)
            throw e
        }
    }
}

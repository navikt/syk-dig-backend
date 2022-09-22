package no.nav.sykdig.digitalisering.saf

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.digitalisering.saf.graphql.SAF_QUERY
import no.nav.sykdig.generated.types.Journalstatus
import no.nav.sykdig.generated.types.SafQuery
import no.nav.sykdig.logger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class SafJournalpostGraphQlClient(
    private val safGraphQlClient: CustomGraphQLClient
) {
    val log = logger()

    @Retryable
    fun erFerdigstilt(journalpostId: String): Boolean {
        try {
            val response = safGraphQlClient.executeQuery(SAF_QUERY, mapOf("id" to journalpostId))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }

            val safResponse = response.dataAsObject(SafQuery::class.java)
            val journalstatus = safResponse.journalpost?.journalstatus

            return journalstatus?.let {
                it == Journalstatus.JOURNALFOERT || it == Journalstatus.FERDIGSTILT
            } ?: false
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF", e)
            throw e
        }
    }
}

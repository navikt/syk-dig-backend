package no.nav.sykdig.digitalisering.saf

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.digitalisering.saf.graphql.AvsenderMottaker
import no.nav.sykdig.digitalisering.saf.graphql.Journalstatus
import no.nav.sykdig.digitalisering.saf.graphql.SAF_QUERY_AVSENDER_MOTTAKER
import no.nav.sykdig.digitalisering.saf.graphql.SAF_QUERY_JOURNAL_STATUS
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalAvsenderMottaker
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalStatus
import no.nav.sykdig.logger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class SafJournalpostGraphQlClient(
    private val safGraphQlClient: CustomGraphQLClient,
) {
    val log = logger()

    @Retryable
    fun erFerdigstilt(journalpostId: String): Boolean {
        try {
            val response = safGraphQlClient.executeQuery(SAF_QUERY_JOURNAL_STATUS, mapOf("id" to journalpostId))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }

            val safResponse = response.dataAsObject(SafQueryJournalStatus::class.java)
            val journalstatus = safResponse.journalpost?.journalstatus

            return journalstatus?.let {
                it == Journalstatus.JOURNALFOERT || it == Journalstatus.FERDIGSTILT
            } ?: false
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF", e)
            throw e
        }
    }

    @Retryable
    fun hentAvvsenderMottar(journalpostId: String): AvsenderMottaker {
        try {
            val response = safGraphQlClient.executeQuery(SAF_QUERY_AVSENDER_MOTTAKER, mapOf("id" to journalpostId))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }

            val safResponse = response.dataAsObject(SafQueryJournalAvsenderMottaker::class.java)
            val avsenderMottaker = safResponse.journalpost?.avsenderMottaker

            return avsenderMottaker!!
        } catch (exception: Exception) {
            log.error("Noe gikk galt ved kall til SAF", exception)
            throw exception
        }
    }
}

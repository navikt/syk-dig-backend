package no.nav.sykdig.digitalisering.saf

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.digitalisering.saf.graphql.AvsenderMottaker
import no.nav.sykdig.digitalisering.saf.graphql.Journalstatus
import no.nav.sykdig.digitalisering.saf.graphql.SAF_QUERY_FIND_JOURNALPOST
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import no.nav.sykdig.logger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class SafJournalpostGraphQlClient(
    private val safGraphQlClient: CustomGraphQLClient,
) {
    val log = logger()

    @Retryable
    fun getJournalpost(journalpostId: String): SafQueryJournalpost {
        try {
            val response = safGraphQlClient.executeQuery(SAF_QUERY_FIND_JOURNALPOST, mapOf("id" to journalpostId))

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }

            return response.dataAsObject(SafQueryJournalpost::class.java)
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF", e)
            throw e
        }
    }

    fun erFerdigstilt(safQueryJournalpost: SafQueryJournalpost): Boolean {
        try {
            val journalstatus = safQueryJournalpost.journalpost?.journalstatus

            return journalstatus?.let {
                it == Journalstatus.JOURNALFOERT || it == Journalstatus.FERDIGSTILT
            } ?: false
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF", e)
            throw e
        }
    }

    fun hentAvvsenderMottar(safQueryJournalpost: SafQueryJournalpost): AvsenderMottaker {
        try {
            val avsenderMottaker = safQueryJournalpost.journalpost?.avsenderMottaker
            return avsenderMottaker!!
        } catch (exception: Exception) {
            log.error("Noe gikk galt ved kall til SAF", exception)
            throw exception
        }
    }
}

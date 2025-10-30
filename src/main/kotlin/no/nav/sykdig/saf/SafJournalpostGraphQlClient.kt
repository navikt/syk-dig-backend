package no.nav.sykdig.saf

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import no.nav.sykdig.saf.graphql.*
import no.nav.sykdig.shared.applog
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component

@Component
class SafJournalpostGraphQlClient(
    private val safGraphQlClient: CustomGraphQLClient,
    private val safM2mGraphQlClient: CustomGraphQLClient,
) {
    val log = applog()

    @Retryable
    fun getJournalpostM2m(journalpostId: String): SafQueryJournalpost {
        try {
            val response =
                safM2mGraphQlClient.executeQuery(
                    SAF_QUERY_FIND_JOURNALPOST,
                    mapOf("id" to journalpostId),
                )

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }
            val safJournalpost = response.dataAsObject(SafQueryJournalpost::class.java)
            if (safJournalpost.journalpost?.tittel == null) {
                log.warn("Tittel is null for $journalpostId")
            }
            return safJournalpost
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF for $journalpostId", e)
            throw e
        }
    }

    @Retryable
    fun getJournalpostNasjonal(journalpostId: String): SafQueryJournalpostNasjonal {
        try {
            val response =
                safM2mGraphQlClient.executeQuery(
                    SAF_QUERY_FIND_JOURNALPOST_NASJONAL,
                    mapOf("id" to journalpostId),
                )
            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }
            val safJournalpost = response.dataAsObject(SafQueryJournalpostNasjonal::class.java)
            return safJournalpost
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF for $journalpostId", e)
            throw e
        }
    }

    @Retryable
    fun getJournalpost(journalpostId: String): SafQueryJournalpost {
        try {
            val response =
                safGraphQlClient.executeQuery(
                    SAF_QUERY_FIND_JOURNALPOST,
                    mapOf("id" to journalpostId),
                )

            val errors = response.errors
            errors.forEach { log.error("Feilmelding fra SAF: ${it.message} for $journalpostId") }

            return response.dataAsObject(SafQueryJournalpost::class.java)
        } catch (e: Exception) {
            log.error("Noe gikk galt ved kall til SAF for $journalpostId", e)
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

    fun getAvsenderMottar(safQueryJournalpost: SafQueryJournalpost): AvsenderMottaker? {
        try {
            return safQueryJournalpost.journalpost?.avsenderMottaker
        } catch (exception: Exception) {
            log.error("Noe gikk galt ved kall til SAF", exception)
            throw exception
        }
    }
}

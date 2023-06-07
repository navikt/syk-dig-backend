package no.nav.sykdig.digitalisering.saf.graphql

const val SAF_QUERY_JOURNAL_STATUS = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            journalstatus
        }
    }
"""

const val SAF_QUERY_AVSENDER_MOTTAKER = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            avsenderMottaker
        }
    }
"""

data class SafQueryJournalAvsenderMottaker(
    val journalpost: JournalpostAvsenderMottaker?,
)

data class JournalpostAvsenderMottaker(
    val avsenderMottaker: AvsenderMottaker?,
)

data class SafQueryJournalStatus(
    val journalpost: JournalpostStatus?,
)
data class JournalpostStatus(
    val journalstatus: Journalstatus?,
)

data class AvsenderMottaker(
    val navn: String,
)

enum class Journalstatus {
    MOTTATT,
    JOURNALFOERT,
    FERDIGSTILT,
    EKSPEDERT,
    UNDER_ARBEID,
    FEILREGISTRERT,
    UTGAAR,
    AVBRUTT,
    UKJENT_BRUKER,
    RESERVERT,
    OPPLASTING_DOKUMENT,
    UKJENT,
}

package no.nav.sykdig.digitalisering.saf.graphql

const val SAF_QUERY_FIND_JOURNALPOST = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            journalstatus
            avsenderMottaker
        }
    }
"""

data class SafQueryJournalpost(
    val journalpost: Journalpost?,
)
data class Journalpost(
    val journalstatus: Journalstatus?,
    val avsenderMottaker: AvsenderMottaker?,
)

data class AvsenderMottaker(
    val navn: String?,
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

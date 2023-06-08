package no.nav.sykdig.digitalisering.saf.graphql

const val SAF_QUERY = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            journalstatus
        }
    }
"""

data class SafQuery(
    val journalpost: Journalpost?,
)
data class Journalpost(
    val journalstatus: Journalstatus?,
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
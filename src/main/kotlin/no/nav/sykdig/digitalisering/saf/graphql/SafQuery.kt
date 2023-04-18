package no.nav.sykdig.digitalisering.saf.graphql

const val SAF_QUERY = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            journalstatus
        }
    }
"""

const val SAF_DOCUMENT_QUERY = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            dokumenter {
                dokumentInfoId
                tittel
            }
        }
    }
"""

data class SafQuery(
    val journalpost: Journalpost?,
)

data class SafDocumentQuery(
    val journalpost: JournalpostDocumenter?,
)

data class SafDocument(
    val dokumentInfoId: String,
    val tittel: String,
)
data class JournalpostDocumenter(
    val dokumenter: List<SafDocument>,
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

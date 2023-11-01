package no.nav.sykdig.digitalisering.saf.graphql

import no.nav.sykdig.digitalisering.dokarkiv.DokumentInfo

const val SAF_QUERY_FIND_JOURNALPOST = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            journalstatus
            avsenderMottaker {
                id 
                type
                navn 
                land
            }
            dokumenter {
                dokumentInfoId
                tittel
                brevkode
                dokumentvarianter {
                    variantformat
                }
            }
        }
    }
"""

data class SafQueryJournalpost(
    val journalpost: Journalpost?,
)
data class Journalpost(
    val journalstatus: Journalstatus?,
    val avsenderMottaker: AvsenderMottaker?,
    val dokumenter: List<DokumentInfo>?,
)
enum class AvsenderMottakerIdType {
    FNR,
    HPRNR,
    NULL,
    ORGNR,
    UKJENT,
    UTL_ORG,
}
data class AvsenderMottaker(
    val id: String?,
    val type: AvsenderMottakerIdType?,
    val navn: String?,
    val land: String?,
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

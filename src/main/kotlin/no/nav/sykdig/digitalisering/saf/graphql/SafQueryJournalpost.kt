package no.nav.sykdig.digitalisering.saf.graphql

import no.nav.sykdig.digitalisering.dokarkiv.DokumentInfo

const val SAF_QUERY_FIND_JOURNALPOST = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            kanal
            tema
            journalstatus
            bruker {
                id 
                type
            }
            dokumenter {
                dokumentInfoId
                tittel
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
    val bruker: Bruker?,
    val dokumenter: List<DokumentInfo>?,
    val tema: String?,
    val kanal: String?,
)

const val TEMA_SYKMELDING = "SYM"
const val CHANNEL_SCAN_IM = "SKAN_IM"
const val CHANNEL_SCAN_NETS = "SKAN_NETS"

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

data class Bruker(
    val id: String,
    val type: Type,
)

enum class Type {
    FNR,
    AKTOERID,
    ORGNR,
}

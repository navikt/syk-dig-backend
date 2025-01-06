package no.nav.sykdig.digitalisering.saf.graphql

const val SAF_QUERY_FIND_JOURNALPOST = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            tittel
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
                brevkode
                dokumentvarianter {
                    variantformat
                } 
            }
            avsenderMottaker {
                id
                type
                navn
                land
            }
        }
    }
"""

data class SafQueryJournalpost(
    val journalpost: SafJournalpost?,
)

data class SafJournalpost(
    val tittel: String?,
    val journalstatus: Journalstatus?,
    val avsenderMottaker: AvsenderMottaker?,
    val bruker: Bruker?,
    val dokumenter: List<DokumentInfo>,
    val tema: String?,
    val kanal: String?,
)


const val TEMA_SYKMELDING = "SYM"
const val TEMA_SYKEPENGER = "SYK"
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

data class DokumentInfo(
    val dokumentInfoId: String,
    val tittel: String?,
    val brevkode: String?,
    val dokumentvarianter: List<Dokumentvariant>?,
)

data class Dokumentvariant(
    val variantformat: String,
)

data class Bruker(
    val id: String,
    val type: Type,
)

enum class Type {
    FNR,
    AKTOERID,
    ORGNR,
}

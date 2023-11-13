package no.nav.sykdig.digitalisering.saf.graphql

import no.nav.sykdig.digitalisering.dokarkiv.Bruker
import no.nav.sykdig.digitalisering.dokarkiv.DokumentInfo
import no.nav.sykdig.digitalisering.dokarkiv.Sak

const val SAF_QUERY_FIND_JOURNALPOST = """
    query FindJournalpost(${"$"}id: String!) {
        journalpost(journalpostId: ${"$"}id) {
            journalstatus
            bruker {
                id 
                type
            }
            dokumenter {
                dokumentInfoId
                tittel
            }
            sak {
                sakstype
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
    val sak: no.nav.sykdig.digitalisering.saf.graphql.Sak?,
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

data class Sak(
    val sakstype: SaksType?,
)

enum class SaksType {
    FAGSAK,
    GENERELL,
}

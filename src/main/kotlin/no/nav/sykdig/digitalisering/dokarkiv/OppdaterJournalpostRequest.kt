package no.nav.sykdig.digitalisering.dokarkiv

data class OppdaterJournalpostRequest(
    val tema: String = "SYM",
    val avsenderMottaker: AvsenderMottakerRequest,
    val bruker: DokBruker,
    val sak: Sak = Sak(),
    val tittel: String,
    val dokumenter: List<DokumentInfo>?,
)

data class OppdaterDokumentRequest(
    val dokumenter: List<DokumentInfo>,
)

data class AvsenderMottakerRequest(
    val id: String?,
    val idType: IdType?,
    val navn: String?,
    val land: String?,
)

enum class IdType {
    FNR,
    HPRNR,
    ORGNR,
    UTL_ORG,
}

data class DokBruker(
    val id: String,
    val idType: String = "FNR",
)

enum class BrukerIdType {
    FNR,
    AKTOERID,
    ORGNR,
}

data class Sak(
    val sakstype: String = "GENERELL_SAK",
)

data class DokumentInfo(
    val dokumentInfoId: String,
    val tittel: String?,
)

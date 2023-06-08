package no.nav.sykdig.digitalisering.dokarkiv

data class OppdaterJournalpostRequest(
    val tema: String = "SYM",
    val avsenderMottaker: AvsenderMottakerRequest,
    val bruker: Bruker,
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

data class Bruker(
    val id: String,
    val idType: String = "FNR",
)

data class Sak(
    val sakstype: String = "GENERELL_SAK",
)

data class DokumentInfo(
    val dokumentInfoId: String,
    val tittel: String?,
)

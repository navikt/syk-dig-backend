package no.nav.sykdig.digitalisering.dokarkiv

data class OppdaterJournalpostRequest(
    val tema: String = "SYM",
    val bruker: Bruker,
    val sak: Sak = Sak(),
    val tittel: String,
    val dokumenter: List<DokumentInfo>?,
)

data class OppdaterDokumentRequest(
    val dokumenter: List<DokumentInfo>,
)
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

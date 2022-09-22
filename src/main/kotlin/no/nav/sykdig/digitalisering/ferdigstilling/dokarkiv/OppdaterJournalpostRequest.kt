package no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv

data class OppdaterJournalpostRequest(
    val tema: String = "SYM",
    val avsenderMottaker: AvsenderMottaker,
    val bruker: Bruker,
    val sak: Sak = Sak(),
    val tittel: String,
    val dokumenter: List<DokumentInfo>?
)

data class AvsenderMottaker(
    val navn: String?,
    val land: String?
)

data class Bruker(
    val id: String,
    val idType: String = "FNR"
)

data class Sak(
    val sakstype: String = "GENERELL_SAK"
)

data class DokumentInfo(
    val dokumentInfoId: String,
    val tittel: String?
)

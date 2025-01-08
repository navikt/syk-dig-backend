package no.nav.sykdig.utenlandsk.models

data class Metadata(
    val type: String = "journalpost",
    val source: String = "syk-dig",
)

data class JournalpostMetadata(
    val journalpostId: String,
    val tema: String,
)

data class CreateSykmeldingKafkaMessage(
    val metadata: Metadata,
    val data: JournalpostMetadata,
)

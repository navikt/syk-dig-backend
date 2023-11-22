package no.nav.sykdig.digitalisering.sykmelding.db

import java.time.OffsetDateTime

data class JournalpostSykmelding(
    val journalpostId: String,
    val created: OffsetDateTime,
)

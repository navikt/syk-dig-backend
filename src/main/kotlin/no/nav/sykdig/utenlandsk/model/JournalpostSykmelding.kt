package no.nav.sykdig.utenlandsk.model

import java.time.OffsetDateTime

data class JournalpostSykmelding(
    val journalpostId: String,
    val created: OffsetDateTime,
)

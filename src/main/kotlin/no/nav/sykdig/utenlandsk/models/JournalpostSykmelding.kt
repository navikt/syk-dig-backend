package no.nav.sykdig.utenlandsk.models

import java.time.OffsetDateTime

data class JournalpostSykmelding(
    val journalpostId: String,
    val created: OffsetDateTime,
)

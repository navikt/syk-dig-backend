package no.nav.sykdig.felles.utils

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun LocalDate?.toOffsetDateTimeAtNoon(): OffsetDateTime? {
    if (this == null) return null
    return OffsetDateTime.of(this, LocalTime.NOON, ZoneOffset.UTC)
}

package no.nav.sykdig.utils

import java.time.LocalDate

class TestHelper {

    companion object {
        internal fun Int.januar(year: Int) = LocalDate.of(year, 1, this)

        internal fun Int.februar(year: Int) = LocalDate.of(year, 2, this)

        internal fun Int.mars(year: Int) = LocalDate.of(year, 3, this)

        internal fun Int.juni(year: Int) = LocalDate.of(year, 6, this)
    }
}
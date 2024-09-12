package no.nav.sykdig.utils

import no.nav.sykdig.digitalisering.sykmelding.Periode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun createTitleRina(
    perioder: List<Periode>?,
    avvisningsGrunn: String?,
): String {
    return if (!avvisningsGrunn.isNullOrEmpty()) {
        "Avvist Søknad om kontantytelser: $avvisningsGrunn"
    } else if (perioder.isNullOrEmpty()) {
        "Søknad om kontantytelser"
    } else {
        "Søknad om kontantytelser ${getFomTomTekst(perioder)}"
    }
}

fun createTittle(
    perioder: List<Periode>?,
    avvisningsGrunn: String?,
): String {
    return if (!avvisningsGrunn.isNullOrEmpty()) {
        "Avvist utenlandsk sykmelding: $avvisningsGrunn"
    } else if (perioder.isNullOrEmpty()) {
        "Digitalisert utenlandsk sykmelding"
    } else {
        "Digitalisert utenlandsk sykmelding ${getFomTomTekst(perioder)}"
    }
}

fun createNavNoTittle(
    perioder: List<Periode>?,
    avvisningsGrunn: String?,
): String {
    return if (!avvisningsGrunn.isNullOrEmpty()) {
        "Avvist Egenerklæring for utenlandske sykemeldinger: $avvisningsGrunn"
    } else if (perioder.isNullOrEmpty()) {
        "Egenerklæring for utenlandske sykemeldinger"
    } else {
        "Egenerklæring for utenlandske sykemeldinger ${getFomTomTekst(perioder)}"
    }
}

private fun getFomTomTekst(perioder: List<Periode>) =
    "${formaterDato(perioder.sortedSykmeldingPeriodeFOMDate().first().fom)} -" +
        " ${formaterDato(perioder.sortedSykmeldingPeriodeTOMDate().last().tom)}"

fun List<Periode>.sortedSykmeldingPeriodeFOMDate(): List<Periode> = sortedBy { it.fom }

fun List<Periode>.sortedSykmeldingPeriodeTOMDate(): List<Periode> = sortedBy { it.tom }

fun formaterDato(dato: LocalDate): String {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return dato.format(formatter)
}

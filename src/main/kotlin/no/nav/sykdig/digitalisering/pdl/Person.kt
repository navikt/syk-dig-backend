package no.nav.sykdig.digitalisering.pdl

data class Person(
    val fnr: String,
    val navn: Navn
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

fun Navn.toFormattedNameString(): String {
    return if (mellomnavn.isNullOrEmpty()) {
        capitalizeFirstLetter("$fornavn $etternavn")
    } else {
        capitalizeFirstLetter("$fornavn $mellomnavn $etternavn")
    }
}

private fun capitalizeFirstLetter(string: String): String {
    return string.lowercase()
        .split(" ").joinToString(" ") { it.capitalize() }
        .split("-").joinToString("-") { it.capitalize() }.trimEnd()
}

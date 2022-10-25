package no.nav.sykdig.digitalisering.pdl

data class Person(
    val fnr: String,
    val navn: Navn,
    val bostedsadresse: Bostedsadresse?,
    val oppholdsadresse: Oppholdsadresse?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class Bostedsadresse(
    val coAdressenavn: String?,
    val vegadresse: Vegadresse?,
    val matrikkeladresse: Matrikkeladresse?,
    val utenlandskAdresse: UtenlandskAdresse?,
    val ukjentBosted: UkjentBosted?
)

data class Oppholdsadresse(
    val coAdressenavn: String?,
    val vegadresse: Vegadresse?,
    val matrikkeladresse: Matrikkeladresse?,
    val utenlandskAdresse: UtenlandskAdresse?,
    val oppholdAnnetSted: String?
)

data class Vegadresse(
    val husnummer: String? = null,
    val husbokstav: String? = null,
    val bruksenhetsnummer: String? = null,
    val adressenavn: String? = null,
    val tilleggsnavn: String? = null,
    val postnummer: String? = null,
)

data class Matrikkeladresse(
    val bruksenhetsnummer: String? = null,
    val tilleggsnavn: String? = null,
    val postnummer: String? = null,
)

data class UtenlandskAdresse(
    val adressenavnNummer: String? = null,
    val bygningEtasjeLeilighet: String? = null,
    val postboksNummerNavn: String? = null,
    val postkode: String? = null,
    val bySted: String? = null,
    val regionDistriktOmraade: String? = null,
    val landkode: String
)

data class UkjentBosted(
    val bostedskommune: String? = null
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
        .split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        .split("-").joinToString("-") { it.replaceFirstChar(Char::titlecase) }.trimEnd()
}

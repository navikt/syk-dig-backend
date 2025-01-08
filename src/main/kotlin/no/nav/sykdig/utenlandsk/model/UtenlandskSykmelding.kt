package no.nav.sykdig.utenlandsk.model

data class UtenlandskSykmelding(
    val land: String,
    val folkeRegistertAdresseErBrakkeEllerTilsvarende: Boolean,
    val erAdresseUtland: Boolean?,
)

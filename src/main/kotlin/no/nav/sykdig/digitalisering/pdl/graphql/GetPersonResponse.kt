package no.nav.sykdig.digitalisering.pdl.graphql

data class PdlResponse(
    val hentIdenter: Identliste?,
    val hentPerson: HentPerson?
)

data class HentPerson(
    val navn: List<Navn>?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class Identliste(
    val identer: List<IdentInformasjon>
)

data class IdentInformasjon(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String
)

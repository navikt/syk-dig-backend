package no.nav.sykdig.nasjonal.helsenett

import no.nav.sykdig.nasjonal.model.Godkjenning

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

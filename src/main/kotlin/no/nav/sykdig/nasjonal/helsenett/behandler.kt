package no.nav.sykdig.nasjonal.helsenett

import no.nav.sykdig.nasjonal.models.Godkjenning

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

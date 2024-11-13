package no.nav.sykdig.digitalisering.helsenett

import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Godkjenning

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?,
)

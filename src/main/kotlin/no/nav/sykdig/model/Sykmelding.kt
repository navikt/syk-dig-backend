package no.nav.sykdig.model

import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.MeldingTilNAV
import no.nav.syfo.model.Periode
import no.nav.syfo.model.Prognose
import no.nav.syfo.model.SporsmalSvar
import java.time.LocalDate
import java.time.OffsetDateTime

data class Sykmelding(
    val id: String,
    val msgId: String,
    var medisinskVurdering: MedisinskVurdering?,
    val arbeidsgiver: Arbeidsgiver?,
    var perioder: List<Periode>?,
    val prognose: Prognose?,
    val utdypendeOpplysninger: Map<String, Map<String, SporsmalSvar>>?,
    val tiltakArbeidsplassen: String?,
    val tiltakNAV: String?,
    val andreTiltak: String?,
    val meldingTilNAV: MeldingTilNAV?,
    val meldingTilArbeidsgiver: String?,
    val kontaktMedPasient: KontaktMedPasient?,
    var behandletTidspunkt: OffsetDateTime?,
    val behandler: Behandler?,
    val syketilfelleStartDato: LocalDate?,
)

package no.nav.sykdig.utenlandsk.models

import no.nav.sykdig.shared.Arbeidsgiver
import no.nav.sykdig.shared.Behandler
import no.nav.sykdig.shared.KontaktMedPasient
import no.nav.sykdig.shared.MedisinskVurdering
import no.nav.sykdig.shared.MeldingTilNAV
import no.nav.sykdig.shared.Periode
import no.nav.sykdig.shared.Prognose
import no.nav.sykdig.shared.SporsmalSvar
import java.time.LocalDate
import java.time.OffsetDateTime

data class SDSykmelding(
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

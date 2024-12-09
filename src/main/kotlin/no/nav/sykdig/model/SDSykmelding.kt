package no.nav.sykdig.model

import no.nav.sykdig.digitalisering.felles.Arbeidsgiver
import no.nav.sykdig.digitalisering.felles.Behandler
import no.nav.sykdig.digitalisering.felles.KontaktMedPasient
import no.nav.sykdig.digitalisering.felles.MedisinskVurdering
import no.nav.sykdig.digitalisering.felles.MeldingTilNAV
import no.nav.sykdig.digitalisering.felles.Periode
import no.nav.sykdig.digitalisering.felles.Prognose
import no.nav.sykdig.digitalisering.felles.SporsmalSvar
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

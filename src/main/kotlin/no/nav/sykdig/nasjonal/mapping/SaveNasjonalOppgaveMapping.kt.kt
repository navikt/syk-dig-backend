package no.nav.sykdig.nasjonal.mapping

import no.nav.sykdig.generated.types.AktivitetIkkeMuligValues
import no.nav.sykdig.generated.types.AnnenFraversArsakGrunn
import no.nav.sykdig.generated.types.AnnenFraversArsakValues
import no.nav.sykdig.generated.types.ArbeidsgiverValues
import no.nav.sykdig.generated.types.ArbeidsrelatertArsakValues
import no.nav.sykdig.generated.types.BehandlerValues
import no.nav.sykdig.generated.types.DiagnoseValues
import no.nav.sykdig.generated.types.GradertValues
import no.nav.sykdig.generated.types.KontaktMedPasientValues
import no.nav.sykdig.generated.types.MedisinskArsakValues
import no.nav.sykdig.generated.types.MedisinskVurderingValues
import no.nav.sykdig.generated.types.MeldingTilNAVValues
import no.nav.sykdig.generated.types.NasjonalSykmeldingValues
import no.nav.sykdig.generated.types.PeriodeValues
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.shared.Adresse
import no.nav.sykdig.shared.AktivitetIkkeMulig
import no.nav.sykdig.shared.AnnenFraverGrunn
import no.nav.sykdig.shared.AnnenFraversArsak
import no.nav.sykdig.shared.Arbeidsgiver
import no.nav.sykdig.shared.ArbeidsrelatertArsak
import no.nav.sykdig.shared.ArbeidsrelatertArsakType
import no.nav.sykdig.shared.Behandler
import no.nav.sykdig.shared.Diagnose
import no.nav.sykdig.shared.Gradert
import no.nav.sykdig.shared.HarArbeidsgiver
import no.nav.sykdig.shared.KontaktMedPasient
import no.nav.sykdig.shared.MedisinskArsak
import no.nav.sykdig.shared.MedisinskArsakType
import no.nav.sykdig.shared.MedisinskVurdering
import no.nav.sykdig.shared.MeldingTilNAV
import no.nav.sykdig.shared.Periode

fun mapToSmRegistreringManuell(sykmeldingValues: NasjonalSykmeldingValues): SmRegistreringManuell {
    return SmRegistreringManuell(
        pasientFnr = sykmeldingValues.pasientFnr,
        sykmelderFnr = sykmeldingValues.sykmelderFnr,
        perioder = sykmeldingValues.perioder.map { mapToPerioder(it) },
        medisinskVurdering = mapToMedisinskVurdering(sykmeldingValues.medisinskVurdering),
        arbeidsgiver = mapToArbeidsgiver(sykmeldingValues.arbeidsgiver),
        behandletDato = sykmeldingValues.behandletDato,
        skjermesForPasient = sykmeldingValues.skjermesForPasient,
        behandler = mapToBehandler(sykmeldingValues.behandler),
        kontaktMedPasient = mapToKontaktMedPasient(sykmeldingValues.kontaktMedPasient),
        meldingTilNAV = mapTilMeldingTilNAV(sykmeldingValues.meldingTilNAV),
        meldingTilArbeidsgiver = sykmeldingValues.meldingTilArbeidsgiver,
        harUtdypendeOpplysninger = sykmeldingValues.harUtdypendeOpplysninger,
        syketilfelleStartDato = null,
        navnFastlege = null,
    )
}

fun mapToPerioder(periode: PeriodeValues): Periode {
    return Periode(
        fom = periode.fom,
        tom = periode.tom,
        aktivitetIkkeMulig = mapToAktivitetIkkeMulig(periode.aktivitetIkkeMulig),
        avventendeInnspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver,
        behandlingsdager = periode.behandlingsdager,
        gradert = mapToGradert(periode.gradert),
        reisetilskudd = periode.reisetilskudd ?: false,
    )
}

fun mapToGradert(gradert: GradertValues?): Gradert? {
    if (gradert == null) return null

    return Gradert(grad = gradert.grad, reisetilskudd = gradert.reisetilskudd)
}

fun mapToAktivitetIkkeMulig(aktivitetIkkeMulig: AktivitetIkkeMuligValues?): AktivitetIkkeMulig? {
    if (aktivitetIkkeMulig == null) return null

    return AktivitetIkkeMulig(
        medisinskArsak = mapToMedisinskArsak(aktivitetIkkeMulig.medisinskArsak),
        arbeidsrelatertArsak = mapToArbeidsrelatertArsak(aktivitetIkkeMulig.arbeidsrelatertArsak),
    )
}

fun mapToMedisinskArsak(medisinskArsak: MedisinskArsakValues?): MedisinskArsak? {
    if (medisinskArsak == null) return null

    return MedisinskArsak(
        beskrivelse = medisinskArsak.beskrivelse,
        arsak = medisinskArsak.arsak.map { mapToMedisinskArsakType(it) },
    )
}

fun mapToMedisinskArsakType(
    medisinskArsakType: no.nav.sykdig.generated.types.MedisinskArsakType
): MedisinskArsakType {
    return when (medisinskArsakType) {
        no.nav.sykdig.generated.types.MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET ->
            MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET
        no.nav.sykdig.generated.types.MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND ->
            MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND
        no.nav.sykdig.generated.types.MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING ->
            MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING
        no.nav.sykdig.generated.types.MedisinskArsakType.ANNET -> MedisinskArsakType.ANNET
    }
}

fun mapToArbeidsrelatertArsak(
    arbeidsrelatertArsak: ArbeidsrelatertArsakValues?
): ArbeidsrelatertArsak? {
    if (arbeidsrelatertArsak == null) return null

    return ArbeidsrelatertArsak(
        beskrivelse = arbeidsrelatertArsak.beskrivelse,
        arsak = arbeidsrelatertArsak.arsak.map { mapToArbeidsrelatertArsakType(it) },
    )
}

fun mapToArbeidsrelatertArsakType(
    arbeidsrelatertArsakType: no.nav.sykdig.generated.types.ArbeidsrelatertArsakType
): ArbeidsrelatertArsakType {
    return when (arbeidsrelatertArsakType) {
        no.nav.sykdig.generated.types.ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING ->
            ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING
        no.nav.sykdig.generated.types.ArbeidsrelatertArsakType.ANNET ->
            ArbeidsrelatertArsakType.ANNET
    }
}

fun mapToMedisinskVurdering(medisinskVurdering: MedisinskVurderingValues): MedisinskVurdering {
    return MedisinskVurdering(
        hovedDiagnose = medisinskVurdering.hovedDiagnose?.let { mapToDiagnose(it) },
        biDiagnoser = medisinskVurdering.biDiagnoser.map { mapToDiagnose(it) } ?: emptyList(),
        svangerskap = medisinskVurdering.svangerskap,
        yrkesskade = medisinskVurdering.yrkesskade,
        yrkesskadeDato = medisinskVurdering.yrkesskadeDato,
        annenFraversArsak = mapToAnnenFraversArsak(medisinskVurdering.annenFraversArsak),
    )
}

fun mapToDiagnose(diagnose: DiagnoseValues): Diagnose {
    return Diagnose(system = diagnose.system, kode = diagnose.kode, tekst = diagnose.tekst)
}

fun mapToAnnenFraversArsak(annenFraversArsak: AnnenFraversArsakValues?): AnnenFraversArsak? {
    if (annenFraversArsak == null) return null

    return AnnenFraversArsak(
        beskrivelse = annenFraversArsak.beskrivelse,
        grunn =
            annenFraversArsak.grunn.mapNotNull {
                mapToAnnenFraversArsakGrunn(it)
            }, // Use mapNotNull to skip invalid mappings
    )
}

fun mapToAnnenFraversArsakGrunn(annenFraverGrunn: AnnenFraversArsakGrunn): AnnenFraverGrunn {
    return when (annenFraverGrunn) {
        AnnenFraversArsakGrunn.GODKJENT_HELSEINSTITUSJON ->
            AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON
        AnnenFraversArsakGrunn.BEHANDLING_FORHINDRER_ARBEID ->
            AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID
        AnnenFraversArsakGrunn.ARBEIDSRETTET_TILTAK -> AnnenFraverGrunn.ARBEIDSRETTET_TILTAK
        AnnenFraversArsakGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND ->
            AnnenFraverGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND
        AnnenFraversArsakGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE ->
            AnnenFraverGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE
        AnnenFraversArsakGrunn.SMITTEFARE -> AnnenFraverGrunn.SMITTEFARE
        AnnenFraversArsakGrunn.ABORT -> AnnenFraverGrunn.ABORT
        AnnenFraversArsakGrunn.UFOR_GRUNNET_BARNLOSHET -> AnnenFraverGrunn.UFOR_GRUNNET_BARNLOSHET
        AnnenFraversArsakGrunn.DONOR -> AnnenFraverGrunn.DONOR
        AnnenFraversArsakGrunn.BEHANDLING_STERILISERING -> AnnenFraverGrunn.BEHANDLING_STERILISERING
    }
}

fun mapToArbeidsgiver(arbeidsgiver: ArbeidsgiverValues): Arbeidsgiver {
    return Arbeidsgiver(
        navn = arbeidsgiver.navn,
        stillingsprosent = arbeidsgiver.stillingsprosent,
        yrkesbetegnelse = arbeidsgiver.yrkesbetegnelse,
        harArbeidsgiver = mapToHarArbeidsGiver(arbeidsgiver.harArbeidsgiver),
    )
}

fun mapToHarArbeidsGiver(
    harArbeidsgiver: no.nav.sykdig.generated.types.HarArbeidsgiver
): HarArbeidsgiver {
    return when (harArbeidsgiver) {
        no.nav.sykdig.generated.types.HarArbeidsgiver.EN_ARBEIDSGIVER ->
            HarArbeidsgiver.EN_ARBEIDSGIVER
        no.nav.sykdig.generated.types.HarArbeidsgiver.FLERE_ARBEIDSGIVERE ->
            HarArbeidsgiver.FLERE_ARBEIDSGIVERE
        else -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
    }
}

fun mapToBehandler(behandler: BehandlerValues): Behandler {
    return Behandler(
        hpr = behandler.hpr,
        tlf = behandler.tlf,
        // TODO: Remove?
        fnr = "",
        fornavn = "",
        mellomnavn = null,
        etternavn = "",
        her = null,
        aktoerId = "",
        adresse =
            Adresse(gate = null, postnummer = null, kommune = null, postboks = null, land = null),
    )
}

fun mapToKontaktMedPasient(kontaktMedPasient: KontaktMedPasientValues): KontaktMedPasient {
    return KontaktMedPasient(
        kontaktDato = kontaktMedPasient.kontaktDato,
        begrunnelseIkkeKontakt = kontaktMedPasient.begrunnelseIkkeKontakt,
    )
}

fun mapTilMeldingTilNAV(meldingTilNAV: MeldingTilNAVValues?): MeldingTilNAV? {
    if (meldingTilNAV == null) return null

    return MeldingTilNAV(
        beskrivBistand = meldingTilNAV.beskrivBistand,
        bistandUmiddelbart = meldingTilNAV.bistandUmiddelbart,
    )
}

package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.generated.types.AktivitetIkkeMulig
import no.nav.sykdig.generated.types.AnnenFraversArsak
import no.nav.sykdig.generated.types.AnnenFraversArsakGrunn
import no.nav.sykdig.generated.types.Arbeidsgiver
import no.nav.sykdig.generated.types.ArbeidsrelatertArsak
import no.nav.sykdig.generated.types.ArbeidsrelatertArsakType
import no.nav.sykdig.generated.types.Behandler
import no.nav.sykdig.generated.types.DiagnoseSchema
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Gradert
import no.nav.sykdig.generated.types.HarArbeidsgiver
import no.nav.sykdig.generated.types.KontaktMedPasient
import no.nav.sykdig.generated.types.MedisinskArsakType
import no.nav.sykdig.generated.types.MedisinskVurdering
import no.nav.sykdig.generated.types.MeldingTilNAV
import no.nav.sykdig.generated.types.NasjonalOppgave
import no.nav.sykdig.generated.types.NasjonalSykmelding
import no.nav.sykdig.generated.types.Periode
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.shared.AnnenFraverGrunn
import no.nav.sykdig.shared.Diagnose
import no.nav.sykdig.shared.MedisinskArsak

fun mapToNasjonalOppgave(oppgave: NasjonalManuellOppgaveDAO): NasjonalOppgave {
    requireNotNull(oppgave.dokumentInfoId)
    return NasjonalOppgave(
        oppgaveId = oppgave.oppgaveId.toString(),
        nasjonalSykmelding = mapToNasjonalSykmelding(oppgave),
        documents = listOf(Document(tittel = "papirsykmelding", dokumentInfoId = oppgave.dokumentInfoId)),
    )
}

fun mapToNasjonalSykmelding(oppgave: NasjonalManuellOppgaveDAO): NasjonalSykmelding {
    // TODO remove bangs after migration
    return NasjonalSykmelding(
        sykmeldingId = oppgave.sykmeldingId,
        journalpostId = oppgave.journalpostId,
        fnr = oppgave.fnr,
        datoOpprettet = oppgave.datoOpprettet.toString(),
        syketilfelleStartDato = oppgave.papirSmRegistrering?.syketilfelleStartDato.toString(),
        arbeidsgiver = mapToArbeidsgiver(oppgave),
        medisinskVurdering = mapToMedisinskVurdering(oppgave),
        skjermesForPasient = oppgave.papirSmRegistrering?.skjermesForPasient,
        perioder = oppgave.papirSmRegistrering?.perioder?.map { mapToPerioder(it) } ?: emptyList(),
        meldingTilNAV = mapTilMeldingTilNAV(oppgave.papirSmRegistrering?.meldingTilNAV),
        meldingTilArbeidsgiver = oppgave.papirSmRegistrering?.meldingTilArbeidsgiver,
        kontaktMedPasient = mapToKontaktMedPasient(oppgave.papirSmRegistrering?.kontaktMedPasient),
        behandletTidspunkt = oppgave.papirSmRegistrering?.behandletTidspunkt,
        behandler = mapToBehandler(oppgave.papirSmRegistrering?.behandler),
    )
}


fun mapToArbeidsgiver(oppgave: NasjonalManuellOppgaveDAO): Arbeidsgiver {
    // TODO remove bangs after migration
    return Arbeidsgiver(
        navn = oppgave.papirSmRegistrering!!.arbeidsgiver?.navn,
        stillingsprosent = oppgave.papirSmRegistrering.arbeidsgiver?.stillingsprosent,
        yrkesbetegnelse = oppgave.papirSmRegistrering.arbeidsgiver?.yrkesbetegnelse,
        harArbeidsgiver = mapToHarArbeidsGiver(oppgave),
    )
}

fun mapToHarArbeidsGiver(oppgave: NasjonalManuellOppgaveDAO): HarArbeidsgiver? {
    // TODO remove bangs after migration
    return when (oppgave.papirSmRegistrering!!.arbeidsgiver?.harArbeidsgiver) {
        null -> null
        no.nav.sykdig.shared.HarArbeidsgiver.EN_ARBEIDSGIVER -> HarArbeidsgiver.EN_ARBEIDSGIVER
        no.nav.sykdig.shared.HarArbeidsgiver.FLERE_ARBEIDSGIVERE -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
        else -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
    }
}

fun mapToMedisinskVurdering(oppgave: NasjonalManuellOppgaveDAO): MedisinskVurdering {
    // TODO remove bangs after migration
    val oppgaveMedisinskVurdering = oppgave.papirSmRegistrering!!.medisinskVurdering
    return MedisinskVurdering(
        hovedDiagnose = oppgaveMedisinskVurdering?.hovedDiagnose?.let { mapToDiagnoseSchema(it) },
        biDiagnoser = oppgaveMedisinskVurdering?.biDiagnoser?.map { mapToDiagnoseSchema(it) } ?: emptyList(),
        svangerskap = oppgaveMedisinskVurdering?.svangerskap ?: false,
        yrkesskade = oppgaveMedisinskVurdering?.yrkesskade ?: false,
        yrkesskadeDato = oppgaveMedisinskVurdering?.yrkesskadeDato.toString(),
        annenFraversArsak = mapToAnnenFraversArsak(oppgaveMedisinskVurdering?.annenFraversArsak),
    )
}


fun mapToDiagnoseSchema(diagnose: Diagnose): DiagnoseSchema {
    return DiagnoseSchema(
        system = diagnose.system,
        kode = diagnose.kode,
        tekst = diagnose.tekst,
    )
}


fun mapToAnnenFraversArsak(annenFraversArsak: no.nav.sykdig.shared.AnnenFraversArsak?): AnnenFraversArsak? {
    if (annenFraversArsak == null) return null

    return AnnenFraversArsak(
        beskrivelse = annenFraversArsak.beskrivelse,
        grunn = annenFraversArsak.grunn.mapNotNull { mapToAnnenFraversArsakGrunn(it) }, // Use mapNotNull to skip invalid mappings
    )
}

fun mapToAnnenFraversArsakGrunn(annenFraverGrunn: AnnenFraverGrunn): AnnenFraversArsakGrunn? {
    return when (annenFraverGrunn) {
        AnnenFraverGrunn.GODKJENT_HELSEINSTITUSJON -> AnnenFraversArsakGrunn.GODKJENT_HELSEINSTITUSJON
        AnnenFraverGrunn.BEHANDLING_FORHINDRER_ARBEID -> AnnenFraversArsakGrunn.BEHANDLING_FORHINDRER_ARBEID
        AnnenFraverGrunn.ARBEIDSRETTET_TILTAK -> AnnenFraversArsakGrunn.ARBEIDSRETTET_TILTAK
        AnnenFraverGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND -> AnnenFraversArsakGrunn.MOTTAR_TILSKUDD_GRUNNET_HELSETILSTAND
        AnnenFraverGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE -> AnnenFraversArsakGrunn.NODVENDIG_KONTROLLUNDENRSOKELSE
        AnnenFraverGrunn.SMITTEFARE -> AnnenFraversArsakGrunn.SMITTEFARE
        AnnenFraverGrunn.ABORT -> AnnenFraversArsakGrunn.ABORT
        AnnenFraverGrunn.UFOR_GRUNNET_BARNLOSHET -> AnnenFraversArsakGrunn.UFOR_GRUNNET_BARNLOSHET
        AnnenFraverGrunn.DONOR -> AnnenFraversArsakGrunn.DONOR
        AnnenFraverGrunn.BEHANDLING_STERILISERING -> AnnenFraversArsakGrunn.BEHANDLING_STERILISERING
    }
}

fun mapToPerioder(periode: no.nav.sykdig.shared.Periode): Periode {
    return Periode(
        fom = periode.fom,
        tom = periode.tom,
        aktivitetIkkeMulig = mapToAktivitetIkkeMulig(periode.aktivitetIkkeMulig),
        avventendeInnspillTilArbeidsgiver = periode.avventendeInnspillTilArbeidsgiver,
        behandlingsdager = periode.behandlingsdager,
        gradert = mapToGradert(periode.gradert),
        reisetilskudd = periode.reisetilskudd,
    )
}

fun mapToGradert(gradert: no.nav.sykdig.shared.Gradert?): Gradert? {
    if (gradert == null) return null

    return Gradert(
        grad = gradert.grad,
        reisetilskudd = gradert.reisetilskudd,
    )
}

fun mapToAktivitetIkkeMulig(aktivitetIkkeMulig: no.nav.sykdig.shared.AktivitetIkkeMulig?): AktivitetIkkeMulig? {
    if (aktivitetIkkeMulig == null) return null

    return AktivitetIkkeMulig(

        medisinskArsak = mapToMedisinskArsak(aktivitetIkkeMulig.medisinskArsak),
        arbeidsrelatertArsak = mapToArbeidsrelatertArsak(aktivitetIkkeMulig.arbeidsrelatertArsak),
    )
}

fun mapToMedisinskArsak(medisinskArsak: MedisinskArsak?): no.nav.sykdig.generated.types.MedisinskArsak? {
    if (medisinskArsak == null) return null

    return no.nav.sykdig.generated.types.MedisinskArsak(
        beskrivelse = medisinskArsak.beskrivelse,
        arsak = medisinskArsak.arsak.mapNotNull { mapToMedisinskArsakType(it) },
    )
}

fun mapToMedisinskArsakType(medisinskArsakType: no.nav.sykdig.shared.MedisinskArsakType): MedisinskArsakType {
    return when (medisinskArsakType) {
        no.nav.sykdig.shared.MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET -> MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET
        no.nav.sykdig.shared.MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND -> MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND
        no.nav.sykdig.shared.MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING -> MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING
        no.nav.sykdig.shared.MedisinskArsakType.ANNET -> MedisinskArsakType.ANNET
    }
}

fun mapToArbeidsrelatertArsak(arbeidsrelatertArsak: no.nav.sykdig.shared.ArbeidsrelatertArsak?): ArbeidsrelatertArsak? {
    if (arbeidsrelatertArsak == null) return null

    return ArbeidsrelatertArsak(
        beskrivelse = arbeidsrelatertArsak.beskrivelse,
        arsak = arbeidsrelatertArsak.arsak.mapNotNull { mapToArbeidsrelatertArsakType(it) },
    )
}

fun mapToArbeidsrelatertArsakType(arbeidsrelatertArsakType: no.nav.sykdig.shared.ArbeidsrelatertArsakType): ArbeidsrelatertArsakType {
    return when (arbeidsrelatertArsakType) {
        no.nav.sykdig.shared.ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING
        no.nav.sykdig.shared.ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakType.ANNET
    }
}


fun mapTilMeldingTilNAV(meldingTilNAV: no.nav.sykdig.shared.MeldingTilNAV?): MeldingTilNAV? {
    if (meldingTilNAV == null) return null

    return MeldingTilNAV(
        beskrivBistand = meldingTilNAV.beskrivBistand,
        bistandUmiddelbart = meldingTilNAV.bistandUmiddelbart,
    )
}


fun mapToKontaktMedPasient(kontaktMedPasient: no.nav.sykdig.shared.KontaktMedPasient?): KontaktMedPasient? {
    if (kontaktMedPasient == null) return null

    return KontaktMedPasient(
        kontaktDato = kontaktMedPasient.kontaktDato.toString(),
        begrunnelseIkkeKontakt = kontaktMedPasient.begrunnelseIkkeKontakt,
    )
}

fun mapToBehandler(behandler: no.nav.sykdig.shared.Behandler?): Behandler? {
    if (behandler == null) return null

    return Behandler(
        fornavn = behandler.fornavn,
        mellomnavn = behandler.mellomnavn,
        etternavn = behandler.etternavn,
        fnr = behandler.fnr,
        hpr = behandler.hpr,
        tlf = behandler.tlf,
    )
}
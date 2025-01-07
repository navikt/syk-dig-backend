package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.digitalisering.felles.AnnenFraverGrunn
import no.nav.sykdig.digitalisering.felles.AnnenFraversArsak
import no.nav.sykdig.digitalisering.felles.Diagnose
import no.nav.sykdig.digitalisering.felles.MedisinskArsak
import no.nav.sykdig.digitalisering.felles.Periode
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.generated.types.AktivitetIkkeMulig
import no.nav.sykdig.generated.types.AnnenFraversArsakGrunn
import no.nav.sykdig.generated.types.Arbeidsgiver
import no.nav.sykdig.generated.types.ArbeidsrelatertArsak
import no.nav.sykdig.generated.types.ArbeidsrelatertArsakType
import no.nav.sykdig.generated.types.DiagnoseSchema
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.HarArbeidsgiver
import no.nav.sykdig.generated.types.MedisinskArsakType
import no.nav.sykdig.generated.types.MedisinskVurdering
import no.nav.sykdig.generated.types.NasjonalOppgave
import no.nav.sykdig.generated.types.NasjonalSykmelding

fun mapToNasjonalOppgave(oppgave: NasjonalManuellOppgaveDAO): NasjonalOppgave {
    requireNotNull(oppgave.dokumentInfoId)
    return NasjonalOppgave(
        oppgaveId = oppgave.oppgaveId.toString(),
        nasjonalSykmelding = mapToNasjonalSykmelding(oppgave),
        documents = listOf(Document(tittel = "papirsykmelding", dokumentInfoId = oppgave.dokumentInfoId)),
    )
}

fun mapToNasjonalSykmelding(oppgave: NasjonalManuellOppgaveDAO): NasjonalSykmelding {
    return NasjonalSykmelding(
        sykmeldingId = oppgave.sykmeldingId,
        journalpostId = oppgave.journalpostId,
        fnr = oppgave.fnr,
        datoOpprettet = oppgave.datoOpprettet.toString(),
        syketilfelleStartDato = oppgave.papirSmRegistrering.syketilfelleStartDato.toString(),
        arbeidsgiver = mapToArbeidsgiver(oppgave),
        medisinskVurdering = mapToMedisinskVurdering(oppgave),
        skjermesForPasient = oppgave.papirSmRegistrering.skjermesForPasient,
        perioder = oppgave.papirSmRegistrering.perioder.map(mapToPerioder(it)) ?: emptyList(),
        meldingTilNAV = TODO(),
        meldingTilArbeidsgiver = TODO(),
        kontaktMedPasient = TODO(),
        behandletTidspunkt = TODO(),
        behandler = TODO(),
    )
}


fun mapToArbeidsgiver(oppgave: NasjonalManuellOppgaveDAO): Arbeidsgiver {
    return Arbeidsgiver(
        navn = oppgave.papirSmRegistrering.arbeidsgiver?.navn,
        stillingsprosent = oppgave.papirSmRegistrering.arbeidsgiver?.stillingsprosent,
        yrkesbetegnelse = oppgave.papirSmRegistrering.arbeidsgiver?.yrkesbetegnelse,
        harArbeidsgiver = mapToHarArbeidsGiver(oppgave),
    )
}

fun mapToHarArbeidsGiver(oppgave: NasjonalManuellOppgaveDAO): HarArbeidsgiver? {
    return when (oppgave.papirSmRegistrering.arbeidsgiver?.harArbeidsgiver) {
        null -> null
        no.nav.sykdig.digitalisering.felles.HarArbeidsgiver.EN_ARBEIDSGIVER -> HarArbeidsgiver.EN_ARBEIDSGIVER
        no.nav.sykdig.digitalisering.felles.HarArbeidsgiver.FLERE_ARBEIDSGIVERE -> HarArbeidsgiver.FLERE_ARBEIDSGIVERE
        else -> HarArbeidsgiver.INGEN_ARBEIDSGIVER
    }
}

fun mapToMedisinskVurdering(oppgave: NasjonalManuellOppgaveDAO): MedisinskVurdering {
    val oppgaveMedisinskVurdering = oppgave.papirSmRegistrering.medisinskVurdering
    return MedisinskVurdering(
        hovedDiagnose = mapToDiagnoseSchema(oppgaveMedisinskVurdering?.hovedDiagnose),
        biDiagnoser = oppgaveMedisinskVurdering?.biDiagnoser?.map { mapToDiagnoseSchema(it) } ?: emptyList(),
        svangerskap = oppgaveMedisinskVurdering?.svangerskap ?: false,
        yrkesskade = oppgaveMedisinskVurdering?.yrkesskade ?: false,
        yrkesskadeDato = oppgaveMedisinskVurdering?.yrkesskadeDato.toString(),
        annenFraversArsak = mapToAnnenFraversArsak(oppgaveMedisinskVurdering?.annenFraversArsak),
    )
}


fun mapToDiagnoseSchema(diagnose: Diagnose?): DiagnoseSchema? {
    if (diagnose == null) return null

    return DiagnoseSchema(
        system = diagnose.system,
        kode = diagnose.kode,
        tekst = diagnose.tekst,
    )
}


fun mapToAnnenFraversArsak(annenFraversArsak: AnnenFraversArsak?): no.nav.sykdig.generated.types.AnnenFraversArsak? {
    if (annenFraversArsak == null) return null

    return no.nav.sykdig.generated.types.AnnenFraversArsak(
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

fun mapToPerioder(periode: Periode): no.nav.sykdig.generated.types.Periode {

    return no.nav.sykdig.generated.types.Periode(
        fom = periode.fom,
        tom = periode.tom,
        aktivitetIkkeMulig = mapToAktivitetIkkeMulig(periode.aktivitetIkkeMulig),
        avventendeInnspillTilArbeidsgiver = TODO(),
        behandlingsdager = TODO(),
        gradert = TODO(),
        reisetilskudd = TODO(),
    )

}

fun mapToAktivitetIkkeMulig(aktivitetIkkeMulig: no.nav.sykdig.digitalisering.felles.AktivitetIkkeMulig?): AktivitetIkkeMulig? {
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

fun mapToMedisinskArsakType(medisinskArsakType: no.nav.sykdig.digitalisering.felles.MedisinskArsakType): MedisinskArsakType {
    return when (medisinskArsakType) {
        no.nav.sykdig.digitalisering.felles.MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET -> MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET
        no.nav.sykdig.digitalisering.felles.MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND -> MedisinskArsakType.AKTIVITET_FORVERRER_TILSTAND
        no.nav.sykdig.digitalisering.felles.MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING -> MedisinskArsakType.AKTIVITET_FORHINDRER_BEDRING
        no.nav.sykdig.digitalisering.felles.MedisinskArsakType.ANNET -> MedisinskArsakType.ANNET
    }
}

fun mapToArbeidsrelatertArsak(arbeidsrelatertArsak: no.nav.sykdig.digitalisering.felles.ArbeidsrelatertArsak?): ArbeidsrelatertArsak? {
    if (arbeidsrelatertArsak == null) return null

    return ArbeidsrelatertArsak(
        beskrivelse = arbeidsrelatertArsak.beskrivelse,
        arsak = arbeidsrelatertArsak.arsak.mapNotNull { mapToArbeidsrelatertArsakType(it) },
    )
}

fun mapToArbeidsrelatertArsakType(arbeidsrelatertArsakType: no.nav.sykdig.digitalisering.felles.ArbeidsrelatertArsakType): ArbeidsrelatertArsakType {
    return when (arbeidsrelatertArsakType) {
        no.nav.sykdig.digitalisering.felles.ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING -> ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING
        no.nav.sykdig.digitalisering.felles.ArbeidsrelatertArsakType.ANNET -> ArbeidsrelatertArsakType.ANNET
    }
}



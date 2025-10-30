package no.nav.sykdig.utenlandsk.mapping

import java.time.LocalDateTime
import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.sykdig.shared.Adresse
import no.nav.sykdig.shared.AktivitetIkkeMulig
import no.nav.sykdig.shared.AnnenFraverGrunn
import no.nav.sykdig.shared.AnnenFraversArsak
import no.nav.sykdig.shared.Arbeidsgiver
import no.nav.sykdig.shared.ArbeidsrelatertArsak
import no.nav.sykdig.shared.ArbeidsrelatertArsakType
import no.nav.sykdig.shared.AvsenderSystem
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
import no.nav.sykdig.shared.SporsmalSvar
import no.nav.sykdig.shared.SvarRestriksjon
import no.nav.sykdig.shared.Sykmelding

fun HelseOpplysningerArbeidsuforhet.toSykmelding(
    sykmeldingId: String,
    pasientAktoerId: String,
    msgId: String,
    signaturDato: LocalDateTime,
) =
    Sykmelding(
        id = sykmeldingId,
        msgId = msgId,
        pasientAktoerId = pasientAktoerId,
        medisinskVurdering = medisinskVurdering.toMedisinskVurdering(),
        skjermesForPasient = medisinskVurdering?.isSkjermesForPasient ?: false,
        arbeidsgiver = arbeidsgiver.toArbeidsgiver(),
        perioder =
            aktivitet.periode.map(HelseOpplysningerArbeidsuforhet.Aktivitet.Periode::toPeriode),
        prognose = null,
        utdypendeOpplysninger =
            if (utdypendeOpplysninger != null) utdypendeOpplysninger.toMap() else emptyMap(),
        tiltakArbeidsplassen = null,
        tiltakNAV = null,
        andreTiltak = null,
        meldingTilNAV = meldingTilNav?.toMeldingTilNAV(),
        meldingTilArbeidsgiver = meldingTilArbeidsgiver,
        kontaktMedPasient = kontaktMedPasient.toKontaktMedPasient(),
        behandletTidspunkt = kontaktMedPasient.behandletDato,
        behandler = behandler.toBehandler(),
        avsenderSystem = avsenderSystem.toAvsenderSystem(),
        syketilfelleStartDato = syketilfelleStartDato,
        signaturDato = signaturDato,
        navnFastlege = pasient?.navnFastlege,
    )

fun HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.toPeriode() =
    Periode(
        fom = periodeFOMDato,
        tom = periodeTOMDato,
        aktivitetIkkeMulig = aktivitetIkkeMulig?.toAktivitetIkkeMulig(),
        avventendeInnspillTilArbeidsgiver = avventendeSykmelding?.innspillTilArbeidsgiver,
        behandlingsdager = behandlingsdager?.antallBehandlingsdagerUke,
        gradert = gradertSykmelding?.toGradert(),
        reisetilskudd = isReisetilskudd == true,
    )

fun HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.GradertSykmelding.toGradert() =
    Gradert(reisetilskudd = isReisetilskudd == true, grad = sykmeldingsgrad)

fun HelseOpplysningerArbeidsuforhet.Arbeidsgiver.toArbeidsgiver() =
    Arbeidsgiver(
        harArbeidsgiver = HarArbeidsgiver.entries.first { it.codeValue == harArbeidsgiver.v },
        navn = navnArbeidsgiver,
        yrkesbetegnelse = yrkesbetegnelse,
        stillingsprosent = stillingsprosent,
    )

fun HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig.toAktivitetIkkeMulig() =
    AktivitetIkkeMulig(
        medisinskArsak = medisinskeArsaker?.toMedisinskArsak(),
        arbeidsrelatertArsak = arbeidsplassen?.toArbeidsrelatertArsak(),
    )

fun HelseOpplysningerArbeidsuforhet.MedisinskVurdering.toMedisinskVurdering() =
    MedisinskVurdering(
        hovedDiagnose = hovedDiagnose?.diagnosekode?.toDiagnose(),
        biDiagnoser = biDiagnoser?.diagnosekode?.map(CV::toDiagnose) ?: listOf(),
        svangerskap = isSvangerskap == true,
        yrkesskade = isYrkesskade == true,
        yrkesskadeDato = yrkesskadeDato,
        annenFraversArsak = annenFraversArsak?.toAnnenFraversArsak(),
    )

fun CV.toDiagnose() = Diagnose(s, v, dn)

fun ArsakType.toAnnenFraversArsak() =
    AnnenFraversArsak(
        beskrivelse = beskriv,
        grunn =
            arsakskode.mapNotNull { code ->
                if (code.v == null || code.v == "0") {
                    null
                } else {
                    AnnenFraverGrunn.entries.first { it.codeValue == code.v.trim() }
                }
            },
    )

fun CS.toMedisinskArsakType() =
    if (v == null || v == "0") {
        null
    } else {
        MedisinskArsakType.entries.first { it.codeValue == v.trim() }
    }

fun CS.toArbeidsrelatertArsakType() =
    if (v == null || v == "0") {
        null
    } else {
        ArbeidsrelatertArsakType.entries.first { it.codeValue == v }
    }

fun HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.toMap() =
    spmGruppe.associate { spmGruppe ->
        spmGruppe.spmGruppeId to
            spmGruppe.spmSvar.associate { svar ->
                svar.spmId to
                    SporsmalSvar(
                        sporsmal = svar.spmTekst,
                        svar = svar.svarTekst,
                        restriksjoner =
                            svar.restriksjon?.restriksjonskode?.mapNotNull(CS::toSvarRestriksjon)
                                ?: listOf(),
                    )
            }
    }

fun CS.toSvarRestriksjon() =
    if (v.isNullOrBlank()) {
        null
    } else {
        SvarRestriksjon.entries.first { it.codeValue == v }
    }

fun Address.toAdresse() =
    Adresse(
        gate = streetAdr,
        postnummer = postalCode?.toIntOrNull(),
        kommune = city,
        postboks = postbox,
        land = country?.v,
    )

fun ArsakType.toArbeidsrelatertArsak() =
    ArbeidsrelatertArsak(
        beskrivelse = beskriv,
        arsak = arsakskode.mapNotNull(CS::toArbeidsrelatertArsakType),
    )

fun ArsakType.toMedisinskArsak() =
    MedisinskArsak(beskrivelse = beskriv, arsak = arsakskode.mapNotNull(CS::toMedisinskArsakType))

fun HelseOpplysningerArbeidsuforhet.MeldingTilNav.toMeldingTilNAV() =
    MeldingTilNAV(bistandUmiddelbart = isBistandNAVUmiddelbart, beskrivBistand = beskrivBistandNAV)

fun HelseOpplysningerArbeidsuforhet.KontaktMedPasient.toKontaktMedPasient() =
    KontaktMedPasient(kontaktDato = kontaktDato, begrunnelseIkkeKontakt = begrunnIkkeKontakt)

fun HelseOpplysningerArbeidsuforhet.Behandler.toBehandler() =
    Behandler(
        fornavn = navn.fornavn ?: "",
        mellomnavn = navn.mellomnavn,
        etternavn = navn.etternavn ?: "",
        aktoerId = "",
        fnr = id.find { it.typeId.v == "FNR" }?.id ?: id.find { it.typeId.v == "DNR" }?.id ?: "",
        hpr = id.find { it.typeId.v == "HPR" }?.id,
        her = id.find { it.typeId.v == "HER" }?.id,
        adresse = adresse.toAdresse(),
        tlf = kontaktInfo.firstOrNull()?.teleAddress?.v,
    )

fun HelseOpplysningerArbeidsuforhet.AvsenderSystem.toAvsenderSystem() =
    AvsenderSystem(navn = systemNavn, versjon = systemVersjon)

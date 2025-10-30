package no.nav.sykdig.nasjonal.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.service.toSykmelding
import no.nav.sykdig.gosys.models.NasjonalOppgaveResponse
import no.nav.sykdig.nasjonal.models.PapirManuellOppgave
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.pdl.Navn
import no.nav.sykdig.pdl.Person
import no.nav.sykdig.shared.*
import no.nav.sykdig.shared.utils.getLocalDateTime
import no.nav.sykdig.shared.utils.mapsmRegistreringManuelltTilFellesformat
import no.nav.sykdig.utils.TestHelper.Companion.januar
import okhttp3.internal.EMPTY_BYTE_ARRAY

fun getXmleiFellesformat(
    smRegistreringManuell: SmRegistreringManuell,
    sykmeldingId: String,
    datoOpprettet: LocalDateTime,
): XMLEIFellesformat {
    return mapsmRegistreringManuelltTilFellesformat(
        smRegistreringManuell = smRegistreringManuell,
        pdlPasient = Person("fnr", Navn("Test", "Doctor", "Thornton"), "123", null, null, null),
        sykmelder =
            Sykmelder(
                aktorId = "aktorid",
                etternavn = "Doctor",
                fornavn = "Test",
                mellomnavn = "Bob",
                fnr = smRegistreringManuell.sykmelderFnr,
                hprNummer = "hpr",
                godkjenninger = null,
            ),
        sykmeldingId = sykmeldingId,
        datoOpprettet = datoOpprettet,
        journalpostId = journalpostId,
    )
}

fun getSykmelding(
    healthInformation: HelseOpplysningerArbeidsuforhet,
    msgHead: XMLMsgHead,
    sykmeldingId: String = "1234",
    aktorId: String = "aktorId",
    aktorIdLege: String = "aktorIdLege",
): Sykmelding {
    return healthInformation.toSykmelding(
        sykmeldingId = sykmeldingId,
        pasientAktoerId = aktorId,
        legeAktoerId = aktorIdLege,
        msgId = sykmeldingId,
        signaturDato = getLocalDateTime(msgHead.msgInfo.genDate),
    )
}

fun getSmRegistreringManuell(
    fnrPasient: String,
    fnrLege: String,
    harUtdypendeOpplysninger: Boolean = false,
): SmRegistreringManuell {
    return SmRegistreringManuell(
        pasientFnr = fnrPasient,
        sykmelderFnr = fnrLege,
        perioder =
            listOf(
                Periode(
                    fom = LocalDate.now(),
                    tom = LocalDate.now(),
                    aktivitetIkkeMulig =
                        AktivitetIkkeMulig(
                            medisinskArsak =
                                MedisinskArsak(
                                    beskrivelse = "test data",
                                    arsak = listOf(MedisinskArsakType.TILSTAND_HINDRER_AKTIVITET),
                                ),
                            arbeidsrelatertArsak = null,
                        ),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = 10,
                    gradert = null,
                    reisetilskudd = false,
                )
            ),
        medisinskVurdering =
            MedisinskVurdering(
                hovedDiagnose =
                    Diagnose(
                        system = "2.16.578.1.12.4.1.1.7170",
                        kode = "A070",
                        tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
                    ),
                biDiagnoser =
                    listOf(
                        Diagnose(
                            system = "2.16.578.1.12.4.1.1.7170",
                            kode = "U070",
                            tekst =
                                "Forstyrrelse relatert til bruk av e-sigarett «Vaping related disorder»",
                        )
                    ),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
            ),
        syketilfelleStartDato = LocalDate.of(2020, 4, 1),
        skjermesForPasient = false,
        arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
        behandletDato = LocalDate.of(2020, 4, 1),
        kontaktMedPasient = KontaktMedPasient(LocalDate.of(2020, 6, 23), "Ja nei det."),
        meldingTilArbeidsgiver = null,
        meldingTilNAV = null,
        navnFastlege = "Per Person",
        behandler =
            Behandler(
                "Per",
                "",
                "Person",
                "123",
                "",
                "",
                "",
                Adresse(null, null, null, null, null),
                "",
            ),
        harUtdypendeOpplysninger = harUtdypendeOpplysninger,
    )
}

fun testDataPapirManuellOppgave(
    oppgaveId: Int,
    perioder: List<Periode>? = null,
): PapirManuellOppgave {
    return PapirManuellOppgave(
        sykmeldingId = "123",
        fnr = "fnr",
        oppgaveid = oppgaveId,
        pdfPapirSykmelding = EMPTY_BYTE_ARRAY,
        papirSmRegistering =
            PapirSmRegistering(
                journalpostId = "123",
                oppgaveId = "123",
                fnr = "fnr",
                aktorId = "aktor",
                dokumentInfoId = "123",
                datoOpprettet = OffsetDateTime.now(),
                sykmeldingId = "123",
                syketilfelleStartDato = LocalDate.now(),
                arbeidsgiver = null,
                medisinskVurdering = null,
                skjermesForPasient = null,
                perioder = perioder,
                prognose = null,
                utdypendeOpplysninger = null,
                tiltakNAV = null,
                tiltakArbeidsplassen = null,
                andreTiltak = null,
                meldingTilNAV = null,
                meldingTilArbeidsgiver = null,
                kontaktMedPasient = null,
                behandletTidspunkt = null,
                behandler =
                    Behandler(
                        "fornavn",
                        "mellomnavn",
                        "etternavn",
                        "",
                        "",
                        "",
                        null,
                        Adresse(null, null, null, null, null),
                        null,
                    ),
            ),
        documents = emptyList(),
    )
}

fun testDataPapirSmregistrering(
    oppgaveId: Int,
    perioder: List<Periode>? = null,
): PapirSmRegistering {
    return PapirSmRegistering(
        journalpostId = "123",
        oppgaveId = oppgaveId.toString(),
        fnr = "123",
        aktorId = "123",
        dokumentInfoId = "123",
        datoOpprettet = OffsetDateTime.now(),
        sykmeldingId = "123",
        syketilfelleStartDato = 1.januar(2023),
        arbeidsgiver = null,
        medisinskVurdering = null,
        skjermesForPasient = false,
        perioder = null,
        prognose = null,
        utdypendeOpplysninger = null,
        tiltakNAV = null,
        tiltakArbeidsplassen = null,
        andreTiltak = null,
        meldingTilNAV = null,
        meldingTilArbeidsgiver = null,
        kontaktMedPasient = null,
        behandletTidspunkt = null,
        behandler = null,
    )
}

fun testDataNasjonalOppgaveResponse(oppgaveId: Int): NasjonalOppgaveResponse {
    return NasjonalOppgaveResponse(
        id = oppgaveId,
        oppgavetype = "TYPE",
        aktivDato = 1.januar(2023),
        prioritet = "",
        endretAvEnhetsnr = "2990",
    )
}

fun extractHelseOpplysningerArbeidsuforhet(
    fellesformat: XMLEIFellesformat
): HelseOpplysningerArbeidsuforhet =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0]
        as HelseOpplysningerArbeidsuforhet

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

const val journalpostId = "123"

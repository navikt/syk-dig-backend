package no.nav.sykdig.digitalisering

import no.nav.syfo.model.Adresse
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.pdl.Bostedsadresse
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.Sykmelding
import no.nav.sykdig.model.SykmeldingUnderArbeid
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate
import java.time.Month
import java.time.OffsetDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMetrics
@SpringBootTest(classes = [SykDigBackendApplication::class])
class OppgaveServiceTest {
    @Autowired
    lateinit var oppgaveRepository: OppgaveRepository

    @Autowired
    lateinit var ferdigstillingService: FerdigstillingService

    @Autowired
    lateinit var syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient

    @Test
    fun `map to receivedSykmelding`() {

        val fnrPasient = "12345678910"
        val fnrLege = ""
        val sykmeldingId = UUID.randomUUID()
        val journalPostId = "452234"
        val houvedDiagnose = Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
        )

        val datoOpprette = OffsetDateTime.parse("2022-11-14T12:00:00Z")

        val oppgaveService = OppgaveService(oppgaveRepository, ferdigstillingService, syfoTilgangskontrollClient)

        val validatedValues = ValidatedOppgaveValues(
            fnrPasient = fnrPasient,
            behandletTidspunkt = OffsetDateTime.parse("2022-10-26T12:00:00Z"),
            skrevetLand = "POL",
            perioder = listOf(
                PeriodeInput(
                    type = PeriodeType.AKTIVITET_IKKE_MULIG,
                    fom = LocalDate.of(2019, Month.AUGUST, 15),
                    tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                    grad = 100
                )
            ),
            hovedDiagnose = DiagnoseInput(kode = houvedDiagnose.kode, system = houvedDiagnose.system),
            biDiagnoser = emptyList(),
        )
        val oppgave = DigitaliseringsoppgaveDbModel(
            oppgaveId = "123",
            fnr = fnrPasient,
            journalpostId = journalPostId,
            dokumentInfoId = null,
            opprettet = datoOpprette,
            ferdigstilt = null,
            sykmeldingId = sykmeldingId,
            type = "type",
            sykmelding = SykmeldingUnderArbeid(
                sykmelding = Sykmelding(
                    id = sykmeldingId.toString(),
                    msgId = "1553--213-12-123",
                    medisinskVurdering = MedisinskVurdering(
                        hovedDiagnose = houvedDiagnose,
                        biDiagnoser = listOf(),
                        svangerskap = false,
                        yrkesskade = false,
                        yrkesskadeDato = null,
                        annenFraversArsak = null
                    ),
                    arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
                    perioder = listOf(
                        Periode(
                            fom = LocalDate.of(2019, Month.AUGUST, 15),
                            tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                            aktivitetIkkeMulig = AktivitetIkkeMulig(
                                medisinskArsak = null,
                                arbeidsrelatertArsak = null
                            ),
                            avventendeInnspillTilArbeidsgiver = null,
                            behandlingsdager = null,
                            gradert = null,
                            reisetilskudd = false
                        )
                    ),
                    prognose = null,
                    utdypendeOpplysninger = emptyMap(),
                    tiltakArbeidsplassen = null,
                    tiltakNAV = null,
                    andreTiltak = null,
                    meldingTilNAV = null,
                    meldingTilArbeidsgiver = null,
                    kontaktMedPasient = null,
                    behandletTidspunkt = datoOpprette,
                    behandler = Behandler(
                        "Per",
                        "",
                        "Person",
                        "",
                        fnrLege,
                        "",
                        "",
                        Adresse(null, null, null, null, null),
                        ""
                    ),
                    syketilfelleStartDato = null
                ),
                fnrPasient = fnrPasient,
                fnrLege = fnrLege,
                legeHprNr = null,
                navLogId = sykmeldingId.toString(),
                msgId = sykmeldingId.toString(),
                legekontorOrgNr = null,
                legekontorHerId = null,
                legekontorOrgName = null,
                mottattDato = null,
                utenlandskSykmelding = UtenlandskSykmelding(
                    land = "POL",
                    andreRelevanteOpplysninger = false
                )

            ),
            endretAv = "test testesen",
            timestamp = datoOpprette
        )
        val person = Person(
            fnrPasient,
            Navn("fornavn", null, "etternavn"),
            Bostedsadresse(
                null,
                null,
                null,
                null,
                null,
            ),
            null,
        )

        val harAndreRelevanteOpplysninger = false

        val receivedSykmelding =
            oppgaveService.mapToReceivedSykmelding(validatedValues, oppgave, person, harAndreRelevanteOpplysninger)

        receivedSykmelding.personNrPasient shouldBeEqualTo fnrPasient
        receivedSykmelding.personNrLege shouldBeEqualTo fnrLege
        receivedSykmelding.navLogId shouldBeEqualTo sykmeldingId.toString()
        receivedSykmelding.msgId shouldBeEqualTo sykmeldingId.toString()
        receivedSykmelding.legekontorOrgName shouldBeEqualTo ""
        receivedSykmelding.mottattDato shouldBeEqualTo datoOpprette.toLocalDateTime()
        receivedSykmelding.tssid shouldBeEqualTo null
        receivedSykmelding.sykmelding.pasientAktoerId shouldBeEqualTo ""
        receivedSykmelding.sykmelding.medisinskVurdering shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldBeEqualTo houvedDiagnose
        receivedSykmelding.sykmelding.skjermesForPasient shouldBeEqualTo false
        receivedSykmelding.sykmelding.arbeidsgiver shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.perioder.size shouldBeEqualTo 1
        receivedSykmelding.sykmelding.prognose shouldBeEqualTo null
        receivedSykmelding.sykmelding.utdypendeOpplysninger shouldBeEqualTo emptyMap()
        receivedSykmelding.sykmelding.tiltakArbeidsplassen shouldBeEqualTo null
        receivedSykmelding.sykmelding.tiltakNAV shouldBeEqualTo null
        receivedSykmelding.sykmelding.andreTiltak shouldBeEqualTo null
        receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart shouldBeEqualTo null
        receivedSykmelding.sykmelding.meldingTilArbeidsgiver shouldBeEqualTo null
        receivedSykmelding.sykmelding.kontaktMedPasient shouldBeEqualTo KontaktMedPasient(
            null,
            null
        )
        receivedSykmelding.sykmelding.behandletTidspunkt shouldBeEqualTo datoOpprette.toLocalDateTime()
        receivedSykmelding.sykmelding.behandler shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.avsenderSystem shouldBeEqualTo AvsenderSystem("syk-dig", journalPostId)
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldBeEqualTo LocalDate.of(2019, 8, 15)
        receivedSykmelding.sykmelding.signaturDato shouldBeEqualTo datoOpprette.toLocalDateTime()
        receivedSykmelding.sykmelding.navnFastlege shouldBeEqualTo null
    }
}

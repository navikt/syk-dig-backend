package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.digitalisering.ValidatedOppgaveValues
import no.nav.sykdig.digitalisering.createDigitalseringsoppgaveDbModel
import no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.pdl.Bostedsadresse
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.Sykmelding
import no.nav.sykdig.model.SykmeldingUnderArbeid
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDate
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMetrics
@SpringBootTest(classes = [SykDigBackendApplication::class])
class FerdigstillingServiceTest {
    @MockBean
    lateinit var safJournalpostGraphQlClient: SafJournalpostGraphQlClient
    @MockBean
    lateinit var dokarkivClient: DokarkivClient
    @MockBean
    lateinit var oppgaveClient: OppgaveClient
    @Autowired
    lateinit var sykmeldingOKProducer: KafkaProducer<String, ReceivedSykmelding>

    lateinit var ferdigstillingService: FerdigstillingService

    @BeforeEach
    fun setup() {
        ferdigstillingService = FerdigstillingService(safJournalpostGraphQlClient, dokarkivClient, oppgaveClient, sykmeldingOKProducer)
    }

    @Test
    fun ferdigstillOppdatererDokarkivOppgaveOgTopic() {
        val sykmeldingId = UUID.randomUUID()
        Mockito.`when`(safJournalpostGraphQlClient.erFerdigstilt("9898")).thenAnswer { false }

        ferdigstillingService.ferdigstill(
            navnSykmelder = "Fornavn Etternavn",
            land = "SWE",
            enhet = "2990",
            oppgave = createDigitalseringsoppgaveDbModel(oppgaveId = "123", fnr = "12345678910", sykmeldingId = sykmeldingId, journalpostId = "9898", dokumentInfoId = "111"),
            sykmeldt = Person(
                fnr = "12345678910",
                navn = Navn("Fornavn", null, "Etternavn"),
                bostedsadresse = null,
                oppholdsadresse = null
            ),
            validatedValues = ValidatedOppgaveValues(
                fnrPasient = "12345678910",
                behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
                skrevetLand = "SWE",
                perioder = listOf(PeriodeInput(PeriodeType.AKTIVITET_IKKE_MULIG, LocalDate.now().minusMonths(1), LocalDate.now().minusWeeks(2))),
                hovedDiagnose = DiagnoseInput("A070", "2.16.578.1.12.4.1.1.7170"),
                biDiagnoser = emptyList()
            ),
            harAndreRelevanteOpplysninger = false
        )

        verify(dokarkivClient).oppdaterOgFerdigstillJournalpost("Fornavn Etternavn", "SWE", "12345678910", "2990", "111", "9898", sykmeldingId.toString())
        verify(oppgaveClient).ferdigstillOppgave("123", sykmeldingId.toString())
    }

    @Test
    fun `map utenlandsk sykmelding to receivedSykmelding`() {
        val fnrPasient = "12345678910"
        val fnrLege = ""
        val sykmeldingId = UUID.randomUUID()
        val journalPostId = "452234"
        val hoveddiagnose = Diagnose(
            system = "2.16.578.1.12.4.1.1.7170",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
        )

        val datoOpprettet = OffsetDateTime.parse("2022-11-14T12:00:00Z")
        val behandletTidspunkt = OffsetDateTime.parse("2022-10-26T12:00:00Z")

        val validatedValues = ValidatedOppgaveValues(
            fnrPasient = fnrPasient,
            behandletTidspunkt = behandletTidspunkt,
            skrevetLand = "POL",
            perioder = listOf(
                PeriodeInput(
                    type = PeriodeType.AKTIVITET_IKKE_MULIG,
                    fom = LocalDate.of(2019, Month.AUGUST, 15),
                    tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                    grad = 100
                )
            ),
            hovedDiagnose = DiagnoseInput(kode = hoveddiagnose.kode, system = hoveddiagnose.system),
            biDiagnoser = emptyList(),
        )
        val oppgave = DigitaliseringsoppgaveDbModel(
            oppgaveId = "123",
            fnr = fnrPasient,
            journalpostId = journalPostId,
            dokumentInfoId = "321",
            opprettet = datoOpprettet,
            ferdigstilt = OffsetDateTime.now(ZoneOffset.UTC),
            sykmeldingId = sykmeldingId,
            type = "UTLAND",
            sykmelding = SykmeldingUnderArbeid(
                sykmelding = Sykmelding(
                    id = sykmeldingId.toString(),
                    msgId = "1553--213-12-123",
                    medisinskVurdering = MedisinskVurdering(
                        hovedDiagnose = hoveddiagnose,
                        biDiagnoser = listOf(),
                        svangerskap = false,
                        yrkesskade = false,
                        yrkesskadeDato = null,
                        annenFraversArsak = null
                    ),
                    arbeidsgiver = null,
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
                    utdypendeOpplysninger = null,
                    tiltakArbeidsplassen = null,
                    tiltakNAV = null,
                    andreTiltak = null,
                    meldingTilNAV = null,
                    meldingTilArbeidsgiver = null,
                    kontaktMedPasient = null,
                    behandletTidspunkt = behandletTidspunkt,
                    behandler = null,
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
            timestamp = datoOpprettet
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
            ferdigstillingService.mapToReceivedSykmelding(validatedValues, oppgave, person, harAndreRelevanteOpplysninger)

        receivedSykmelding.personNrPasient shouldBeEqualTo fnrPasient
        receivedSykmelding.personNrLege shouldBeEqualTo fnrLege
        receivedSykmelding.navLogId shouldBeEqualTo sykmeldingId.toString()
        receivedSykmelding.msgId shouldBeEqualTo sykmeldingId.toString()
        receivedSykmelding.legekontorOrgName shouldBeEqualTo ""
        receivedSykmelding.mottattDato shouldBeEqualTo datoOpprettet.toLocalDateTime()
        receivedSykmelding.tssid shouldBeEqualTo null
        receivedSykmelding.sykmelding.pasientAktoerId shouldBeEqualTo ""
        receivedSykmelding.sykmelding.medisinskVurdering shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose shouldBeEqualTo hoveddiagnose
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
        receivedSykmelding.sykmelding.behandletTidspunkt shouldBeEqualTo behandletTidspunkt.toLocalDateTime()
        receivedSykmelding.sykmelding.behandler shouldNotBeEqualTo null
        receivedSykmelding.sykmelding.avsenderSystem shouldBeEqualTo AvsenderSystem("syk-dig", journalPostId)
        receivedSykmelding.sykmelding.syketilfelleStartDato shouldBeEqualTo LocalDate.of(2019, 8, 15)
        receivedSykmelding.sykmelding.signaturDato shouldBeEqualTo datoOpprettet.toLocalDateTime()
        receivedSykmelding.sykmelding.navnFastlege shouldBeEqualTo null
    }
}

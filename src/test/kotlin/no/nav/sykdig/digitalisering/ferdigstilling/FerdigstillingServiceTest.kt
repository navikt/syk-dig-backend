package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.Periode
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.SporsmalSvar
import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.digitalisering.createDigitalseringsoppgaveDbModel
import no.nav.sykdig.digitalisering.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.mapToReceivedSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Bostedsadresse
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.AvsenderMottaker
import no.nav.sykdig.digitalisering.saf.graphql.AvsenderMottakerIdType
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_IM
import no.nav.sykdig.digitalisering.saf.graphql.Journalpost
import no.nav.sykdig.digitalisering.saf.graphql.Journalstatus
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDate
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [SykDigBackendApplication::class])
class FerdigstillingServiceTest : FellesTestOppsett() {
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
        ferdigstillingService =
            FerdigstillingService(safJournalpostGraphQlClient, dokarkivClient, oppgaveClient, sykmeldingOKProducer)
    }

    @Test
    fun ferdigstillOppdatererDokarkivOppgaveOgTopic() {
        val sykmeldingId = UUID.randomUUID()
        val journalpostId = "9898"
        val dokumentInfoId = "111"
        val journalpost = SafQueryJournalpost(
            journalpost = Journalpost(
                journalstatus = Journalstatus.JOURNALFOERT,
                avsenderMottaker = AvsenderMottaker(
                    id = "12345678910",
                    navn = "Fornavn Etternavn",
                    type = AvsenderMottakerIdType.FNR,
                    land = null,
                ),
                dokumenter = emptyList(),
                bruker = null,
                tema = TEMA_SYKMELDING,
                kanal = CHANNEL_SCAN_IM,
                sak = null,
            ),
        )
        Mockito.`when`(safJournalpostGraphQlClient.getJournalpost(journalpostId)).thenAnswer { journalpost }
        Mockito.`when`(safJournalpostGraphQlClient.erFerdigstilt(journalpost)).thenAnswer { false }
        Mockito.`when`(safJournalpostGraphQlClient.getAvvsenderMottar(journalpost)).thenAnswer {
            AvsenderMottaker(
                id = "12345678910",
                navn = "Fornavn Etternavn",
                type = AvsenderMottakerIdType.FNR,
                land = null,
            )
        }

        val perioder = listOf(
            Periode(
                fom = LocalDate.now().minusMonths(1),
                tom = LocalDate.now().minusWeeks(2),
                aktivitetIkkeMulig =
                AktivitetIkkeMulig(medisinskArsak = null, arbeidsrelatertArsak = null),
                avventendeInnspillTilArbeidsgiver = null,
                behandlingsdager = null,
                gradert = null,
                reisetilskudd = false,
            ),
        )

        val validatedValues = FerdistilltRegisterOppgaveValues(
            fnrPasient = "12345678910",
            behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
            skrevetLand = "SWE",
            perioder = listOf(
                PeriodeInput(
                    PeriodeType.AKTIVITET_IKKE_MULIG,
                    LocalDate.now().minusMonths(1),
                    LocalDate.now().minusWeeks(2),
                ),
            ),
            hovedDiagnose = DiagnoseInput("A070", "ICD10"),
            biDiagnoser = emptyList(),
            folkeRegistertAdresseErBrakkeEllerTilsvarende = true,
        )

        val sykmeldt = Person(
            fnr = "12345678910",
            navn = Navn("Fornavn", null, "Etternavn"),
            aktorId = "aktorid",
            bostedsadresse = null,
            oppholdsadresse = null,
            fodselsdato = LocalDate.of(1970, 1, 1),
        )

        ferdigstillingService.ferdigstill(
            enhet = "2990",
            oppgave = createDigitalseringsoppgaveDbModel(
                oppgaveId = "123",
                fnr = "12345678910",
                sykmeldingId = sykmeldingId,
                journalpostId = journalpostId,
                dokumentInfoId = dokumentInfoId,
            ),
            sykmeldt = sykmeldt,
            validatedValues = validatedValues,
        )

        verify(dokarkivClient).oppdaterOgFerdigstillJournalpost(
            "SWE",
            "12345678910",
            "2990",
            "111",
            journalpostId,
            sykmeldingId.toString(),
            perioder,
            "scanning",
            null,
            journalpost.journalpost?.avsenderMottaker!!,
            "Fornavn Etternavn",
            null,
        )
        verify(oppgaveClient).ferdigstillOppgave("123", sykmeldingId.toString())
    }

    @Test
    fun `map utenlandsk sykmelding to receivedSykmelding`() {
        val fnrPasient = "12345678910"
        val fnrLege = ""
        val sykmeldingId = UUID.randomUUID()
        val journalPostId = "452234"
        val hoveddiagnose = Diagnose(
            system = "ICD10",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
        )

        val datoOpprettet = OffsetDateTime.parse("2022-11-14T12:00:00Z")
        val behandletTidspunkt = OffsetDateTime.parse("2022-10-26T12:00:00Z")

        val validatedValues = FerdistilltRegisterOppgaveValues(
            fnrPasient = fnrPasient,
            behandletTidspunkt = behandletTidspunkt,
            skrevetLand = "POL",
            perioder = listOf(
                PeriodeInput(
                    type = PeriodeType.AKTIVITET_IKKE_MULIG,
                    fom = LocalDate.of(2019, Month.AUGUST, 15),
                    tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                    grad = null,
                ),
            ),
            hovedDiagnose = DiagnoseInput(kode = hoveddiagnose.kode, system = hoveddiagnose.system),
            biDiagnoser = emptyList(),
            folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
        )

        val person = Person(
            fnrPasient,
            Navn("fornavn", null, "etternavn"),
            "aktorid",
            Bostedsadresse(
                null,
                null,
                null,
                null,
                null,
            ),
            null,
            LocalDate.of(1970, 1, 1),
        )

        val receivedSykmelding = mapToReceivedSykmelding(
            validatedValues,
            person,
            sykmeldingId.toString(),
            journalPostId,
            datoOpprettet.toLocalDateTime(),
        )

        assertEquals(fnrPasient, receivedSykmelding.personNrPasient)
        assertEquals(fnrLege, receivedSykmelding.personNrLege)
        assertEquals(sykmeldingId.toString(), receivedSykmelding.navLogId)
        assertEquals(sykmeldingId.toString(), receivedSykmelding.msgId)
        assertEquals("", receivedSykmelding.legekontorOrgName)
        assertEquals(datoOpprettet.toLocalDateTime(), receivedSykmelding.mottattDato)
        assertEquals(null, receivedSykmelding.tssid)
        assertEquals("aktorid", receivedSykmelding.sykmelding.pasientAktoerId)
        assertEquals(
            Diagnose(
                system = "2.16.578.1.12.4.1.1.7110",
                kode = "A070",
                tekst = "Balantidiasis",
            ),
            receivedSykmelding.sykmelding.medisinskVurdering.hovedDiagnose,
        )

        assertEquals(false, receivedSykmelding.sykmelding.skjermesForPasient)
        assertEquals(1, receivedSykmelding.sykmelding.perioder.size)
        assertEquals(null, receivedSykmelding.sykmelding.prognose)
        assertEquals(emptyMap<String, Map<String, SporsmalSvar>>(), receivedSykmelding.sykmelding.utdypendeOpplysninger)
        assertEquals(null, receivedSykmelding.sykmelding.tiltakArbeidsplassen)
        assertEquals(null, receivedSykmelding.sykmelding.tiltakNAV)
        assertEquals(null, receivedSykmelding.sykmelding.andreTiltak)
        assertEquals(null, receivedSykmelding.sykmelding.meldingTilNAV?.bistandUmiddelbart)
        assertEquals(null, receivedSykmelding.sykmelding.meldingTilArbeidsgiver)
        assertEquals(
            KontaktMedPasient(
                null,
                null,
            ),
            receivedSykmelding.sykmelding.kontaktMedPasient,
        )

        assertEquals(behandletTidspunkt.toLocalDateTime(), receivedSykmelding.sykmelding.behandletTidspunkt)
        assertEquals(AvsenderSystem("syk-dig", journalPostId), receivedSykmelding.sykmelding.avsenderSystem)
        assertEquals(LocalDate.of(2019, 8, 15), receivedSykmelding.sykmelding.syketilfelleStartDato)
        assertEquals(datoOpprettet.toLocalDateTime(), receivedSykmelding.sykmelding.signaturDato)
        assertEquals(null, receivedSykmelding.sykmelding.navnFastlege)
    }

    @Test
    fun `map utenlandsk sykmelding with gradert periode to receivedSykmelding with gradert periode`() {
        val fnrPasient = "12345678910"
        val sykmeldingId = UUID.randomUUID()
        val journalPostId = "452234"
        val hoveddiagnose = Diagnose(
            system = "ICD10",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
        )

        val datoOpprettet = OffsetDateTime.parse("2022-11-14T12:00:00Z")
        val behandletTidspunkt = OffsetDateTime.parse("2022-10-26T12:00:00Z")

        val validatedValues = FerdistilltRegisterOppgaveValues(
            fnrPasient = fnrPasient,
            behandletTidspunkt = behandletTidspunkt,
            skrevetLand = "POL",
            perioder = listOf(
                PeriodeInput(
                    type = PeriodeType.GRADERT,
                    fom = LocalDate.of(2019, Month.AUGUST, 15),
                    tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                    grad = 69,
                ),
            ),
            hovedDiagnose = DiagnoseInput(kode = hoveddiagnose.kode, system = hoveddiagnose.system),
            biDiagnoser = emptyList(),
            folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
        )

        val person = Person(
            fnrPasient,
            Navn("fornavn", null, "etternavn"),
            "aktorid",
            Bostedsadresse(
                null,
                null,
                null,
                null,
                null,
            ),
            null,
            LocalDate.of(1970, 1, 1),
        )

        val receivedSykmelding = mapToReceivedSykmelding(
            validatedValues,
            person,
            sykmeldingId.toString(),
            journalPostId,
            datoOpprettet.toLocalDateTime(),
        )

        assertEquals(1, receivedSykmelding.sykmelding.perioder.size)
        assertNotNull(receivedSykmelding.sykmelding.perioder.first().gradert)
    }

    @Test
    fun `should throw illegal state if it tries to map a bad gradert periode`() {
        val fnrPasient = "12345678910"
        val sykmeldingId = UUID.randomUUID()
        val journalPostId = "452234"
        val hoveddiagnose = Diagnose(
            system = "ICD10",
            kode = "A070",
            tekst = "Balantidiasis Dysenteri som skyldes Balantidium",
        )

        val datoOpprettet = OffsetDateTime.parse("2022-11-14T12:00:00Z")
        val behandletTidspunkt = OffsetDateTime.parse("2022-10-26T12:00:00Z")

        val validatedValues = FerdistilltRegisterOppgaveValues(
            fnrPasient = fnrPasient,
            behandletTidspunkt = behandletTidspunkt,
            skrevetLand = "POL",
            perioder = listOf(
                PeriodeInput(
                    type = PeriodeType.GRADERT,
                    fom = LocalDate.of(2019, Month.AUGUST, 15),
                    tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                    grad = 120,
                ),
            ),
            hovedDiagnose = DiagnoseInput(kode = hoveddiagnose.kode, system = hoveddiagnose.system),
            biDiagnoser = emptyList(),
            folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
        )

        val person = Person(
            fnrPasient,
            Navn("fornavn", null, "etternavn"),
            "aktorid",
            Bostedsadresse(
                null,
                null,
                null,
                null,
                null,
            ),
            null,
            LocalDate.of(1970, 1, 1),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            mapToReceivedSykmelding(
                validatedValues,
                person,
                sykmeldingId.toString(),
                journalPostId,
                datoOpprettet.toLocalDateTime(),
            )
        }
        assertEquals(exception.message, "Gradert sykmelding må ha grad")
    }
}

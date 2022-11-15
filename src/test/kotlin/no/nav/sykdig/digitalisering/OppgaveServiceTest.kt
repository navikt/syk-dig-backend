package no.nav.sykdig.digitalisering

import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMetrics
@SpringBootTest(classes = [SykDigBackendApplication::class])
@Transactional
class OppgaveServiceTest : FellesTestOppsett() {
    @MockBean
    lateinit var ferdigstillingService: FerdigstillingService
    @MockBean
    lateinit var syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient

    lateinit var oppgaveService: OppgaveService

    @BeforeEach
    fun setup() {
        oppgaveService = OppgaveService(oppgaveRepository, ferdigstillingService, syfoTilgangskontrollClient)
        oppgaveRepository.lagreOppgave(createDigitalseringsoppgaveDbModel(oppgaveId = "123", fnr = "12345678910"))
        Mockito.`when`(syfoTilgangskontrollClient.sjekkTilgangVeileder("12345678910")).thenAnswer { true }
    }

    @AfterEach
    fun after() {
        namedParameterJdbcTemplate.update("DELETE FROM sykmelding", MapSqlParameterSource())
        namedParameterJdbcTemplate.update("DELETE FROM oppgave", MapSqlParameterSource())
    }

    @Test
    fun henterOppgaveFraDb() {
        val oppgave = oppgaveService.getOppgave("123")

        oppgave.fnr shouldBeEqualTo "12345678910"
        oppgave.endretAv shouldBeEqualTo "A123456"
        oppgave.type shouldBeEqualTo "UTLAND"
        oppgave.sykmelding shouldBeEqualTo null
        oppgave.ferdigstilt shouldBeEqualTo null
    }

    @Test
    fun feilmeldingHvisIkkeTilgangTilOppgave() {
        Mockito.`when`(syfoTilgangskontrollClient.sjekkTilgangVeileder("12345678910")).thenAnswer { false }

        assertFailsWith<IkkeTilgangException> {
            oppgaveService.getOppgave("123")
        }
    }

    @Test
    fun oppdatererOppgaveIDb() {
        oppgaveService.updateOppgave(
            oppgaveId = "123",
            values = SykmeldingUnderArbeidValues(
                fnrPasient = "12345678910",
                skrevetLand = "SWE",
                hovedDiagnose = DiagnoseInput("A070", "2.16.578.1.12.4.1.1.7170")
            ),
            ident = "X987654"
        )

        val oppdatertOppgave = oppgaveService.getOppgave("123")
        oppdatertOppgave.fnr shouldBeEqualTo "12345678910"
        oppdatertOppgave.endretAv shouldBeEqualTo "X987654"
        oppdatertOppgave.type shouldBeEqualTo "UTLAND"
        oppdatertOppgave.sykmelding?.fnrPasient shouldBeEqualTo "12345678910"
        oppdatertOppgave.sykmelding?.utenlandskSykmelding?.land shouldBeEqualTo "SWE"
        oppdatertOppgave.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.kode shouldBeEqualTo "A070"
        oppdatertOppgave.ferdigstilt shouldBeEqualTo null
    }

    @Test
    fun ferdigstillerOppgaveIDb() {
        oppgaveService.ferdigstillOppgave(
            oppgaveId = "123",
            ident = "X987654",
            values = SykmeldingUnderArbeidValues(
                fnrPasient = "12345678910",
                skrevetLand = "SWE",
                hovedDiagnose = DiagnoseInput("A070", "2.16.578.1.12.4.1.1.7170"),
                harAndreRelevanteOpplysninger = false
            ),
            validatedValues = ValidatedOppgaveValues(
                fnrPasient = "12345678910",
                behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
                skrevetLand = "SWE",
                perioder = listOf(PeriodeInput(PeriodeType.AKTIVITET_IKKE_MULIG, LocalDate.now().minusMonths(1), LocalDate.now().minusWeeks(2))),
                hovedDiagnose = DiagnoseInput("A070", "2.16.578.1.12.4.1.1.7170"),
                biDiagnoser = emptyList()
            ),
            enhetId = "2990",
            person = Person(
                fnr = "12345678910",
                navn = Navn("Fornavn", null, "Etternavn"),
                bostedsadresse = null,
                oppholdsadresse = null
            ),
            oppgave = oppgaveService.getOppgave("123")
        )

        val oppdatertOppgave = oppgaveService.getOppgave("123")
        oppdatertOppgave.fnr shouldBeEqualTo "12345678910"
        oppdatertOppgave.endretAv shouldBeEqualTo "X987654"
        oppdatertOppgave.type shouldBeEqualTo "UTLAND"
        oppdatertOppgave.sykmelding?.fnrPasient shouldBeEqualTo "12345678910"
        oppdatertOppgave.sykmelding?.utenlandskSykmelding?.land shouldBeEqualTo "SWE"
        oppdatertOppgave.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.kode shouldBeEqualTo "A070"
        oppdatertOppgave.ferdigstilt shouldNotBeEqualTo null
    }
}

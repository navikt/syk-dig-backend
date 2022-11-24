package no.nav.sykdig.digitalisering

import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.UferdigRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
    lateinit var personService: PersonService

    lateinit var oppgaveService: OppgaveService

    @BeforeEach
    fun setup() {
        oppgaveService = OppgaveService(oppgaveRepository, ferdigstillingService, personService)
        oppgaveRepository.lagreOppgave(createDigitalseringsoppgaveDbModel(oppgaveId = "123", fnr = "12345678910"))
        Mockito.`when`(personService.hentPerson("12345678910", "123")).thenReturn(
            Person(
                fnr = "12345678910",
                navn = Navn("Fornavn", null, "Etternavn"),
                bostedsadresse = null,
                oppholdsadresse = null
            )
        )
    }

    @AfterEach
    fun after() {
        namedParameterJdbcTemplate.update("DELETE FROM sykmelding", MapSqlParameterSource())
        namedParameterJdbcTemplate.update("DELETE FROM oppgave", MapSqlParameterSource())
    }

    @Test
    fun henterOppgaveFraDb() {
        val oppgave = oppgaveService.getOppgave("123")

        assertEquals("12345678910", oppgave.fnr)
        assertEquals("12345678910", oppgave.fnr)
        assertEquals("A123456", oppgave.endretAv)
        assertEquals("UTLAND", oppgave.type)
        assertEquals(null, oppgave.sykmelding)
        assertEquals(null, oppgave.ferdigstilt)
    }

    @Test
    fun oppdatererOppgaveIDb() {
        oppgaveService.updateOppgave(
            oppgaveId = "123",
            registerOppgaveValues = UferdigRegisterOppgaveValues(
                fnrPasient = "12345678910",
                skrevetLand = "SWE",
                hovedDiagnose = DiagnoseInput("A070", "2.16.578.1.12.4.1.1.7170"),
                behandletTidspunkt = null,
                perioder = null,
                biDiagnoser = null,
                harAndreRelevanteOpplysninger = null
            ),
            ident = "X987654"
        )

        val oppdatertOppgave = oppgaveService.getOppgave("123")

        assertEquals("12345678910", oppdatertOppgave.fnr)
        assertEquals("X987654", oppdatertOppgave.endretAv)
        assertEquals("UTLAND", oppdatertOppgave.type)
        assertEquals("12345678910", oppdatertOppgave.sykmelding?.fnrPasient)
        assertEquals("SWE", oppdatertOppgave.sykmelding?.utenlandskSykmelding?.land)
        assertEquals("A070", oppdatertOppgave.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.kode)
        assertEquals(null, oppdatertOppgave.ferdigstilt)
    }

    @Test
    fun ferdigstillerOppgaveIDb() {
        oppgaveService.ferdigstillOppgave(
            oppgaveId = "123",
            ident = "X987654",
            values = FerdistilltRegisterOppgaveValues(
                fnrPasient = "12345678910",
                behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
                skrevetLand = "SWE",
                perioder = listOf(PeriodeInput(PeriodeType.AKTIVITET_IKKE_MULIG, LocalDate.now().minusMonths(1), LocalDate.now().minusWeeks(2))),
                hovedDiagnose = DiagnoseInput("A070", "2.16.578.1.12.4.1.1.7170"),
                biDiagnoser = emptyList(),
                harAndreRelevanteOpplysninger = null,
            ),
            enhetId = "2990"
        )

        val oppdatertOppgave = oppgaveService.getOppgave("123")
        assertEquals("12345678910", oppdatertOppgave.fnr)
        assertEquals("X987654", oppdatertOppgave.endretAv)
        assertEquals("UTLAND", oppdatertOppgave.type)
        assertEquals("12345678910", oppdatertOppgave.sykmelding?.fnrPasient)
        assertEquals("SWE", oppdatertOppgave.sykmelding?.utenlandskSykmelding?.land)
        assertEquals("A070", oppdatertOppgave.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.kode)
        assertEquals(null, oppdatertOppgave.ferdigstilt)
    }
}

package no.nav.sykdig.digitalisering

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.utenlandsk.services.FerdigstillingService
import no.nav.sykdig.oppgave.OppgaveClient
import no.nav.sykdig.utenlandsk.models.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.utenlandsk.models.UferdigRegisterOppgaveValues
import no.nav.sykdig.pdl.Navn
import no.nav.sykdig.pdl.Person
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.utenlandsk.services.OppgaveCommonService
import no.nav.sykdig.utenlandsk.services.SykDigOppgaveService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [SykDigBackendApplication::class])
@Transactional
class SykDigOppgaveServiceTest : IntegrationTest() {
    @MockitoBean
    lateinit var ferdigstillingService: FerdigstillingService

    lateinit var sykDigOppgaveService: SykDigOppgaveService

    @MockitoBean
    lateinit var oppgaveCommonService: OppgaveCommonService

    @MockitoBean
    lateinit var oppgaveClient: OppgaveClient

    @BeforeEach
    fun setup() {
        sykDigOppgaveService = SykDigOppgaveService(oppgaveRepository, ferdigstillingService, oppgaveCommonService, oppgaveClient)
        oppgaveRepository.lagreOppgave(createDigitalseringsoppgaveDbModel(oppgaveId = "123", fnr = "12345678910"))
    }

    @AfterEach
    fun after() {
        namedParameterJdbcTemplate.update("DELETE FROM sykmelding", MapSqlParameterSource())
        namedParameterJdbcTemplate.update("DELETE FROM oppgave", MapSqlParameterSource())
    }

    @Test
    fun henterOppgaveFraDb() {
        val oppgave = sykDigOppgaveService.getOppgave("123")

        assertEquals("12345678910", oppgave.fnr)
        assertEquals("12345678910", oppgave.fnr)
        assertEquals("A123456", oppgave.endretAv)
        assertEquals("UTLAND", oppgave.type)
        assertEquals(null, oppgave.sykmelding)
        assertEquals(null, oppgave.ferdigstilt)
    }

    @Test
    fun oppdatererOppgaveIDb() {
        sykDigOppgaveService.updateOppgave(
            oppgaveId = "123",
            registerOppgaveValues =
                UferdigRegisterOppgaveValues(
                    fnrPasient = "12345678910",
                    skrevetLand = "SWE",
                    hovedDiagnose = DiagnoseInput("A070", "ICD10"),
                    behandletTidspunkt = null,
                    perioder = null,
                    biDiagnoser = null,
                    folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
                    erAdresseUtland = null,
                ),
            navEpost = "X987654",
        )

        val oppdatertOppgave = sykDigOppgaveService.getOppgave("123")

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
        sykDigOppgaveService.ferdigstillUtenlandskAvvistOppgave(
            oppgave = createDigitalseringsoppgaveDbModel(oppgaveId = "123", fnr = "12345678910"),
            navEpost = "X987654",
            values =
                FerdistilltRegisterOppgaveValues(
                    fnrPasient = "12345678910",
                    behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
                    skrevetLand = "SWE",
                    perioder =
                        listOf(
                            PeriodeInput(
                                PeriodeType.AKTIVITET_IKKE_MULIG,
                                LocalDate.now().minusMonths(1),
                                LocalDate.now().minusWeeks(2),
                            ),
                        ),
                    hovedDiagnose = DiagnoseInput("A070", "ICD10"),
                    biDiagnoser = emptyList(),
                    folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
                    erAdresseUtland = null,
                ),
            enhetId = "2990",
            sykmeldt =
                Person(
                    fnr = "12345678910",
                    navn = Navn("Fornavn", null, "Etternavn"),
                    aktorId = "aktorid",
                    bostedsadresse = null,
                    oppholdsadresse = null,
                    fodselsdato = LocalDate.of(1980, 5, 5),
                ),
        )

        val oppdatertOppgave = sykDigOppgaveService.getOppgave("123")
        assertEquals("12345678910", oppdatertOppgave.fnr)
        assertEquals("X987654", oppdatertOppgave.endretAv)
        assertEquals("UTLAND", oppdatertOppgave.type)
        assertEquals("12345678910", oppdatertOppgave.sykmelding?.fnrPasient)
        assertEquals("SWE", oppdatertOppgave.sykmelding?.utenlandskSykmelding?.land)
        assertEquals("A070", oppdatertOppgave.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.kode)
        assertNotEquals(null, oppdatertOppgave.ferdigstilt)
    }
}

package no.nav.sykdig.digitalisering
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration
import no.nav.sykdig.TestGraphQLContextContributor
import no.nav.sykdig.config.CustomDataFetchingExceptionHandler
import no.nav.sykdig.db.PoststedRepository
import no.nav.sykdig.digitalisering.api.DigitaliseringsoppgaveDataFetcher
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.UferdigRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Bostedsadresse
import no.nav.sykdig.digitalisering.pdl.Matrikkeladresse
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.Vegadresse
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidStatus
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.model.SykmeldingUnderArbeid
import no.nav.sykdig.utils.toOffsetDateTimeAtNoon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(
    classes = [
        DgsAutoConfiguration::class,
        DgsExtendedScalarsAutoConfiguration::class,
        DigitaliseringsoppgaveDataFetcher::class,
        AdresseDataFetchers::class,
        CustomDataFetchingExceptionHandler::class,
        TestGraphQLContextContributor::class,
        OppgaveSecurityService::class,
    ],
)
@EnableMethodSecurity(prePostEnabled = true)
class OppgaveDataFetcherTest {
    @MockBean
    lateinit var poststedRepository: PoststedRepository

    @MockBean
    lateinit var oppgaveService: DigitaliseringsoppgaveService

    @MockBean
    lateinit var securityService: OppgaveSecurityService

    @Autowired
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @BeforeEach
    fun before() {
        val authentication: Authentication = Mockito.mock(Authentication::class.java)
        val securityContext: SecurityContext = Mockito.mock(SecurityContext::class.java)
        Mockito.`when`(securityContext.authentication).thenReturn(authentication)
        SecurityContextHolder.setContext(securityContext)
        Mockito.`when`(authentication.isAuthenticated).thenReturn(true)
        Mockito.`when`(securityService.hasAccessToOppgave(anyString(), anyString())).thenAnswer { true }
    }

    @Test
    fun `querying oppgave`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(),
            )
        }

        val oppgave: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    ... on Digitaliseringsoppgave {
                        values {
                            fnrPasient                    
                        }
                    }
                }
            }
            """.trimIndent(),
            "data.oppgave.values.fnrPasient",
        )

        assertEquals("12345678910", oppgave)
    }

    @Test
    fun `querying oppgave when ferdigstilt should return status`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                    ferdigstilt = OffsetDateTime.now(),
                ),
                person = createPerson(),
            )
        }

        val status: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    ... on DigitaliseringsoppgaveStatus {
                        status
                    }
                }
            }
            """.trimIndent(),
            "data.oppgave.status",
        )

        assertEquals(status, "FERDIGSTILT")
    }

    @Test
    fun `querying oppgave when sendt to gosys should return status`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                    ferdigstilt = OffsetDateTime.now(),
                    tilbakeTilGosys = true,
                ),
                person = createPerson(),
            )
        }

        val status: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    ... on DigitaliseringsoppgaveStatus {
                        status
                    }
                }
            }
            """.trimIndent(),
            "data.oppgave.status",
        )

        assertEquals(status, "IKKE_EN_SYKMELDING")
    }

    @Test
    fun `querying oppgave no access to oppgave`() {
        Mockito.`when`(securityService.hasAccessToOppgave(anyString(), anyString())).thenAnswer { false }
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(),
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            {
                oppgave(oppgaveId: "123") {
                    ... on Digitaliseringsoppgave {
                        values {
                            fnrPasient                    
                        }
                    }
                }
            }
            """.trimIndent(),
        )
        assertEquals(1, result.errors.size)
        assertEquals("Innlogget bruker har ikke tilgang", result.errors[0].message)
    }

    @Test
    fun `querying oppgave with poststed on vegadresse should use adresse data fetcher`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(
                    vegadresse = Vegadresse("7", null, null, "Gateveien", null, "1111"),
                ),
            )
        }
        Mockito.`when`(poststedRepository.getPoststed("1111")).thenAnswer {
            "Oslo"
        }
        val poststed: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    ... on Digitaliseringsoppgave {
                        values {
                          fnrPasient
                        }
                        person {
                            bostedsadresse {
                                __typename
                                ... on Vegadresse {
                                    poststed
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
            "data.oppgave.person.bostedsadresse.poststed",
        )
        assertEquals("Oslo", poststed)
    }

    @Test
    fun `querying oppgave with poststed on matrikkeladresse should use adresse data fetcher`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(
                    matrikkeladresse = Matrikkeladresse("Bruksenhetsnummer", "Tillegsnanvn", "2222"),
                ),
            )
        }

        Mockito.`when`(poststedRepository.getPoststed("2222")).thenAnswer {
            "Vestnes"
        }
        val poststed: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    ... on Digitaliseringsoppgave {
                        values {
                          fnrPasient
                        }
                        person {
                            bostedsadresse {
                                __typename
                                ... on Matrikkeladresse {
                                    poststed
                                }
                            }
                        }
                    }
                }
            }
            """.trimIndent(),
            "data.oppgave.person.bostedsadresse.poststed",
        )

        assertEquals("Vestnes", poststed)
    }

    @Test
    fun `lagre oppgave UNDER_AREBID should update oppgave`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("345")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(),
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    ... on Digitaliseringsoppgave {
                        oppgaveId
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "id" to "345",
                "enhetId" to "1234",
                "values" to mapOf(
                    "fnrPasient" to "20086600138",
                    "behandletTidspunkt" to null,
                    "skrevetLand" to null,
                    "perioder" to null,
                    "hovedDiagnose" to null,
                    "biDiagnoser" to null,
                ),
                "status" to SykmeldingUnderArbeidStatus.UNDER_ARBEID,
            ),
        )

        assertEquals(0, result.errors.size)
        verify(
            oppgaveService,
            times(1),
        ).updateOppgave(
            oppgaveId = "345",
            values = UferdigRegisterOppgaveValues(fnrPasient = "20086600138", null, null, null, null, null, null),
            navEpost = "fake-test-ident",
        )
    }

    @Test
    fun `lager oppgave uten tilgang`() {
        Mockito.`when`(securityService.hasAccessToOppgave(anyString(), anyString())).thenAnswer { false }
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("345")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(),
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    ... on Digitaliseringsoppgave {
                        oppgaveId
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "id" to "345",
                "enhetId" to "1234",
                "values" to mapOf(
                    "fnrPasient" to "20086600138",
                    "behandletTidspunkt" to null,
                    "skrevetLand" to null,
                    "perioder" to null,
                    "hovedDiagnose" to null,
                    "biDiagnoser" to null,
                ),
                "status" to SykmeldingUnderArbeidStatus.UNDER_ARBEID,
            ),
        )
        assertEquals(1, result.errors.size)
        assertEquals("Innlogget bruker har ikke tilgang", result.errors[0].message)
    }

    @Test
    fun `lagre oppgave with FERDIGSTILT should ferdigstille oppgave`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("345")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(),
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    ... on Digitaliseringsoppgave {
                        oppgaveId
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "id" to "345",
                "enhetId" to "1234",
                "values" to mapOf(
                    "fnrPasient" to "20086600138",
                    "behandletTidspunkt" to "2022-10-26",
                    "skrevetLand" to "POL",
                    "perioder" to emptyList<PeriodeInput>(),
                    "hovedDiagnose" to mapOf(
                        "kode" to "Z09",
                        "system" to "ICPC2",
                    ),
                    "biDiagnoser" to emptyList<DiagnoseInput>(),
                ),
                "status" to SykmeldingUnderArbeidStatus.FERDIGSTILT,
            ),
        )

        assertEquals(0, result.errors.size)
        verify(
            oppgaveService,
            times(1),
        ).ferdigstillOppgave(
            oppgaveId = "345",
            navEpost = "fake-test-ident",
            values = FerdistilltRegisterOppgaveValues(
                fnrPasient = "20086600138",
                behandletTidspunkt = LocalDate.parse("2022-10-26").toOffsetDateTimeAtNoon()!!,
                skrevetLand = "POL",
                perioder = emptyList(),
                hovedDiagnose = DiagnoseInput(kode = "Z09", system = "ICPC2"),
                biDiagnoser = emptyList(),
                harAndreRelevanteOpplysninger = null,
            ),
            enhetId = "1234",
        )
    }

    @Test
    fun `should throw ClientException when values are not validated correctly`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("345")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(),
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    ... on Digitaliseringsoppgave {
                        oppgaveId
                    }
                }
            }
            """.trimIndent(),
            mapOf(
                "id" to "345",
                "enhetId" to "1234",
                "values" to mapOf(
                    "fnrPasient" to "testfnr-pasient",
                    "behandletTidspunkt" to "2022-10-26",
                    // empty string is not valid
                    "skrevetLand" to "",
                    "perioder" to emptyList<PeriodeInput>(),
                    "hovedDiagnose" to mapOf(
                        "kode" to "Køde",
                        "system" to "ICDCPC12",
                    ),
                    "biDiagnoser" to emptyList<DiagnoseInput>(),
                ),
                "status" to SykmeldingUnderArbeidStatus.FERDIGSTILT,
            ),
        )

        assertEquals(1, result.errors.size)
        assertEquals("Landet sykmeldingen er skrevet må være satt", result.errors[0].message)
    }
}

fun createDigitalseringsoppgaveDbModel(
    oppgaveId: String = "123",
    fnr: String = "12345678910",
    journalpostId: String = "journalPostId",
    dokumentInfoId: String? = null,
    opprettet: OffsetDateTime = OffsetDateTime.now(),
    ferdigstilt: OffsetDateTime? = null,
    tilbakeTilGosys: Boolean = false,
    avvisingsgrunn: String = "Mangler diagnose",
    sykmeldingId: UUID = UUID.randomUUID(),
    type: String = "UTLAND",
    sykmelding: SykmeldingUnderArbeid? = null,
    dokumenter: List<DokumentDbModel> = emptyList(),
    endretAv: String = "A123456",
    timestamp: OffsetDateTime = OffsetDateTime.now(),
) = OppgaveDbModel(
    oppgaveId = oppgaveId,
    fnr = fnr,
    journalpostId = journalpostId,
    dokumentInfoId = dokumentInfoId,
    opprettet = opprettet,
    ferdigstilt = ferdigstilt,
    avvisingsgrunn = avvisingsgrunn,
    tilbakeTilGosys = tilbakeTilGosys,
    sykmeldingId = sykmeldingId,
    type = type,
    sykmelding = sykmelding,
    endretAv = endretAv,
    dokumenter = dokumenter,
    timestamp = timestamp,
    source = "scanning",
)

private fun createPerson(
    vegadresse: Vegadresse? = null,
    matrikkeladresse: Matrikkeladresse? = null,
) = Person(
    "12345678910",
    Navn("fornavn", null, "etternavn"),
    "aktorid",
    Bostedsadresse(
        null,
        vegadresse,
        matrikkeladresse,
        null,
        null,
    ),
    null,
    LocalDate.of(1970, 1, 1),
)

package no.nav.sykdig.digitalisering
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration
import no.nav.sykdig.TestGraphQLContextContributor
import no.nav.sykdig.config.CustomDataFetchingExceptionHandler
import no.nav.sykdig.db.PoststedRepository
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
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.model.SykmeldingUnderArbeid
import no.nav.sykdig.utils.toOffsetDateTimeAtNoon
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
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
    ]
)
@EnableGlobalMethodSecurity(prePostEnabled = true)
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
        Mockito.`when`(securityContext.getAuthentication()).thenReturn(authentication)
        SecurityContextHolder.setContext(securityContext)
        Mockito.`when`(authentication.isAuthenticated).thenReturn(true)
        Mockito.`when`(securityService.hasAccessToOppgave(anyString())).thenAnswer { true }
    }

    @Test
    fun `querying oppgave`() {

        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson()
            )
        }

        val oppgave: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    values {
                        fnrPasient                    
                    }
                }
            }
            """.trimIndent(),
            "data.oppgave.values.fnrPasient"
        )

        oppgave shouldBeEqualTo "12345678910"
    }

    @Test
    fun `querying oppgave no access to oppgave`() {
        Mockito.`when`(securityService.hasAccessToOppgave(anyString())).thenAnswer { false }
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson()
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            {
                oppgave(oppgaveId: "123") {
                    values {
                        fnrPasient                    
                    }
                }
            }
            """.trimIndent(),
        )
        result.errors.size shouldBe 1
        result.errors[0].message shouldBeEqualTo "Innlogget bruker har ikke tilgang"
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
                )
            )
        }
        Mockito.`when`(poststedRepository.getPoststed("1111")).thenAnswer {
            "Oslo"
        }
        val poststed: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
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
            """.trimIndent(),
            "data.oppgave.person.bostedsadresse.poststed",
        )

        poststed shouldBeEqualTo "Oslo"
    }

    @Test
    fun `querying oppgave with poststed on matrikkeladresse should use adresse data fetcher`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("123")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson(
                    matrikkeladresse = Matrikkeladresse("Bruksenhetsnummer", "Tillegsnanvn", "2222")
                )
            )
        }

        Mockito.`when`(poststedRepository.getPoststed("2222")).thenAnswer {
            "Vestnes"
        }
        val poststed: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
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
            """.trimIndent(),
            "data.oppgave.person.bostedsadresse.poststed"
        )

        poststed shouldBeEqualTo "Vestnes"
    }

    @Test
    fun `lagre oppgave UNDER_AREBID should update oppgave`() {
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("345")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson()
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    oppgaveId
                }
            }
            """.trimIndent(),
            mapOf(
                "id" to "345",
                "enhetId" to "1234",
                "values" to mapOf(
                    "fnrPasient" to "testfnr-pasient",
                    "behandletTidspunkt" to null,
                    "skrevetLand" to null,
                    "perioder" to null,
                    "hovedDiagnose" to null,
                    "biDiagnoser" to null,
                ),
                "status" to SykmeldingUnderArbeidStatus.UNDER_ARBEID
            )
        )

        result.errors.size shouldBe 0
        verify(
            oppgaveService, times(1)
        ).updateOppgave(
            oppgaveId = "345",
            values = UferdigRegisterOppgaveValues(fnrPasient = "testfnr-pasient", null, null, null, null, null, null),
            ident = "fake-test-ident",
        )
    }

    @Test
    fun `lager oppgave uten tilgang`() {
        Mockito.`when`(securityService.hasAccessToOppgave(anyString())).thenAnswer { false }
        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("345")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson()
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    oppgaveId
                }
            }
            """.trimIndent(),
            mapOf(
                "id" to "345",
                "enhetId" to "1234",
                "values" to mapOf(
                    "fnrPasient" to "testfnr-pasient",
                    "behandletTidspunkt" to null,
                    "skrevetLand" to null,
                    "perioder" to null,
                    "hovedDiagnose" to null,
                    "biDiagnoser" to null,
                ),
                "status" to SykmeldingUnderArbeidStatus.UNDER_ARBEID
            )
        )
        result.errors.size shouldBe 1
        result.errors[0].message shouldBeEqualTo "Innlogget bruker har ikke tilgang"
    }

    @Test
    fun `lagre oppgave with FERDIGSTILT should ferdigstille oppgave`() {

        Mockito.`when`(oppgaveService.getDigitaiseringsoppgave("345")).thenAnswer {
            SykDigOppgave(
                oppgaveDbModel = createDigitalseringsoppgaveDbModel(
                    sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
                ),
                person = createPerson()
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    oppgaveId
                }
            }
            """.trimIndent(),
            mapOf(
                "id" to "345",
                "enhetId" to "1234",
                "values" to mapOf(
                    "fnrPasient" to "testfnr-pasient",
                    "behandletTidspunkt" to "2022-10-26",
                    "skrevetLand" to "POL",
                    "perioder" to emptyList<PeriodeInput>(),
                    "hovedDiagnose" to mapOf(
                        "kode" to "Køde",
                        "system" to "ICDCPC12",
                    ),
                    "biDiagnoser" to emptyList<DiagnoseInput>(),
                ),
                "status" to SykmeldingUnderArbeidStatus.FERDIGSTILT
            )
        )

        result.errors.size shouldBe 0
        verify(
            oppgaveService, times(1)
        ).ferdigstillOppgave(
            oppgaveId = "345",
            ident = "fake-test-ident",
            values = FerdistilltRegisterOppgaveValues(
                fnrPasient = "testfnr-pasient",
                behandletTidspunkt = LocalDate.parse("2022-10-26").toOffsetDateTimeAtNoon()!!,
                skrevetLand = "POL",
                perioder = emptyList(),
                hovedDiagnose = DiagnoseInput(kode = "Køde", system = "ICDCPC12"),
                biDiagnoser = emptyList(),
                harAndreRelevanteOpplysninger = null
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
                person = createPerson()
            )
        }

        val result = dgsQueryExecutor.execute(
            """
            mutation TestLagreOppgave(${"$"}id: String!, ${"$"}enhetId: String!, ${"$"}values: SykmeldingUnderArbeidValues!, ${"$"}status: SykmeldingUnderArbeidStatus!) {
                lagre(oppgaveId: ${"$"}id, enhetId: ${"$"}enhetId, values: ${"$"}values, status: ${"$"}status) {
                    oppgaveId
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
                "status" to SykmeldingUnderArbeidStatus.FERDIGSTILT
            )
        )

        result.errors.size shouldBe 1
        result.errors[0].message shouldBeEqualTo "Landet sykmeldingen er skrevet må være satt"
    }
}

fun createDigitalseringsoppgaveDbModel(
    oppgaveId: String = "123",
    fnr: String = "12345678910",
    journalpostId: String = "journalPostId",
    dokumentInfoId: String? = null,
    opprettet: OffsetDateTime = OffsetDateTime.now(),
    ferdigstilt: OffsetDateTime? = null,
    sykmeldingId: UUID = UUID.randomUUID(),
    type: String = "UTLAND",
    sykmelding: SykmeldingUnderArbeid? = null,
    endretAv: String = "A123456",
    timestamp: OffsetDateTime = OffsetDateTime.now(),
) = OppgaveDbModel(
    oppgaveId = oppgaveId,
    fnr = fnr,
    journalpostId = journalpostId,
    dokumentInfoId = dokumentInfoId,
    opprettet = opprettet,
    ferdigstilt = ferdigstilt,
    sykmeldingId = sykmeldingId,
    type = type,
    sykmelding = sykmelding,
    endretAv = endretAv,
    timestamp = timestamp,
)

private fun createPerson(
    vegadresse: Vegadresse? = null,
    matrikkeladresse: Matrikkeladresse? = null,
) = Person(
    "12345678910",
    Navn("fornavn", null, "etternavn"),
    Bostedsadresse(
        null,
        vegadresse,
        matrikkeladresse,
        null,
        null,
    ),
    null,
)

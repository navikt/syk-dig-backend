package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration
import no.nav.sykdig.TestGraphQLContextContributor
import no.nav.sykdig.config.CustomDataFetchingExceptionHandler
import no.nav.sykdig.db.PoststedRepository
import no.nav.sykdig.digitalisering.pdl.Bostedsadresse
import no.nav.sykdig.digitalisering.pdl.Matrikkeladresse
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.pdl.Vegadresse
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidStatus
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.SykmeldingUnderArbeid
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(
    classes = [
        DgsAutoConfiguration::class,
        DgsExtendedScalarsAutoConfiguration::class,
        OppgaveDataFetcher::class,
        AdresseDataFetchers::class,
        CustomDataFetchingExceptionHandler::class,
        TestGraphQLContextContributor::class,
    ]
)
class OppgaveDataFetcherTest {

    @MockBean
    lateinit var poststedRepository: PoststedRepository

    @MockBean
    lateinit var personService: PersonService

    @MockBean
    lateinit var oppgaveService: OppgaveService

    @Autowired
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @Test
    fun `querying oppgave`() {
        Mockito.`when`(oppgaveService.getOppgave("123")).thenAnswer {
            createDigitalseringsoppgaveDbModel(
                sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
            )
        }

        Mockito.`when`(personService.hentPerson(anyString(), anyString())).thenAnswer {
            createPerson()
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
    fun `querying oppgave with poststed on vegadresse should use adresse data fetcher`() {
        Mockito.`when`(oppgaveService.getOppgave("123")).thenAnswer {
            createDigitalseringsoppgaveDbModel(
                sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
            )
        }

        Mockito.`when`(personService.hentPerson(anyString(), anyString())).thenAnswer {
            createPerson(
                vegadresse = Vegadresse("7", null, null, "Gateveien", null, "1111"),
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
        Mockito.`when`(oppgaveService.getOppgave("123")).thenAnswer {
            createDigitalseringsoppgaveDbModel(
                sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
            )
        }

        Mockito.`when`(personService.hentPerson(anyString(), anyString())).thenAnswer {
            createPerson(
                matrikkeladresse = Matrikkeladresse("Bruksenhetsnummer", "Tillegsnanvn", "2222")
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
        Mockito.`when`(oppgaveService.getOppgave("345")).thenAnswer {
            createDigitalseringsoppgaveDbModel(
                sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
            )
        }

        Mockito.`when`(personService.hentPerson(anyString(), anyString())).thenAnswer {
            createPerson()
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
            values = SykmeldingUnderArbeidValues(fnrPasient = "testfnr-pasient"),
            ident = "fake-test-ident",
        )
    }

    @Test
    fun `lagre oppgave with FERDIGSTILT should ferdigstille oppgave`() {
        val person = createPerson()
        val oppgave = createDigitalseringsoppgaveDbModel(
            sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
        )

        Mockito.`when`(oppgaveService.getOppgave("345")).thenAnswer { oppgave }
        Mockito.`when`(personService.hentPerson(anyString(), anyString())).thenAnswer { person }

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
            values = SykmeldingUnderArbeidValues(
                fnrPasient = "testfnr-pasient",
                behandletTidspunkt = LocalDate.parse("2022-10-26"),
                skrevetLand = "POL",
                perioder = emptyList(),
                hovedDiagnose = DiagnoseInput(kode = "Køde", system = "ICDCPC12"),
                biDiagnoser = emptyList(),
            ),
            validatedValues = ValidatedOppgaveValues(
                fnrPasient = "testfnr-pasient",
                behandletTidspunkt = OffsetDateTime.parse("2022-10-26T12:00:00Z"),
                skrevetLand = "POL",
                perioder = emptyList(),
                hovedDiagnose = DiagnoseInput(kode = "Køde", system = "ICDCPC12"),
                biDiagnoser = emptyList(),
            ),
            enhetId = "1234",
            person = person,
            oppgave = oppgave,
        )
    }

    @Test
    fun `should throw ClientException when values are not validated correctly`() {
        val person = createPerson()
        val oppgave = createDigitalseringsoppgaveDbModel(
            sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
        )

        Mockito.`when`(oppgaveService.getOppgave("345")).thenAnswer { oppgave }
        Mockito.`when`(personService.hentPerson(anyString(), anyString())).thenAnswer { person }

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
    type: String = "type",
    sykmelding: SykmeldingUnderArbeid? = null,
    endretAv: String = "test testesen",
    timestamp: OffsetDateTime = OffsetDateTime.now(),
) = DigitaliseringsoppgaveDbModel(
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

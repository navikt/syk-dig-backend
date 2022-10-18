package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.autoconfig.DgsExtendedScalarsAutoConfiguration
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.pdl.Bostedsadresse
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.pdl.Vegadresse
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.SykmeldingUnderArbeid
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(classes = [DgsAutoConfiguration::class, DgsExtendedScalarsAutoConfiguration::class, OppgaveDataFetcher::class])
class OppgaveDataFetcherTest {

    @MockBean
    lateinit var syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient

    @MockBean
    lateinit var oppgaveRepository: OppgaveRepository

    @MockBean
    lateinit var personService: PersonService

    @Autowired
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    @Test
    fun oppgave() {
        Mockito.`when`(oppgaveRepository.getOppgave("123")).thenAnswer {
            createDigitalseringsoppgaveDbModel(
                sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66"),
            )
        }
        Mockito.`when`(syfoTilgangskontrollClient.sjekkTilgangVeileder("12345678910")).thenAnswer {
            true
        }
        Mockito.`when`(personService.hentPerson(anyString(), anyString())).thenAnswer {
            Person(
                "12345678910",
                Navn("fornavn", null, "etternavn"),
                Bostedsadresse(
                    null,
                    Vegadresse("7", null, null, "Gateveien", null, "1111", "Stedet"),
                    null,
                    null,
                    null,
                ),
                null,
            )
        }
        val oppgave: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    person {
                        fnr
                    }
                }
            }
            """.trimIndent(),
            "data.oppgave.person.fnr"
        )

        oppgave shouldBeEqualTo "12345678910"
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

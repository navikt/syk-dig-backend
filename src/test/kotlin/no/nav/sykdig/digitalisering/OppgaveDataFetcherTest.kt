package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.SykmeldingUnderArbeid
import no.nav.sykdig.tilgangskontroll.ClientIdValidation
import no.nav.sykdig.tilgangskontroll.SyfoTilgangskontrollOboClient
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Profile
import java.time.OffsetDateTime
import java.util.UUID


@SpringBootTest(classes = [DgsAutoConfiguration::class, OppgaveDataFetcher::class])
class OppgaveDataFetcherTest {

    @MockBean
    lateinit var clientIdValidation: ClientIdValidation

    @MockBean
    lateinit var syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient

    @MockBean
    lateinit var oppgaveRepository: OppgaveRepository

    @Autowired
    lateinit var dgsQueryExecutor: DgsQueryExecutor

    fun before() {
        Mockito.`when`(clientIdValidation.validateClientId(listOf(any())))
    }

    @Test
    fun oppgave() {
        Mockito.`when`(oppgaveRepository.getOppgave("123")).thenAnswer {
            createDigitalseringsoppgaveDbModel(
                sykmeldingId = UUID.fromString("555a874f-eaca-49eb-851a-2426a0798b66")
            )
        }
        Mockito.`when`(syfoTilgangskontrollClient.sjekkTilgangVeileder("12345678910")).thenAnswer {
            true
        }

        val oppgave: String = dgsQueryExecutor.executeAndExtractJsonPath(
            """
            {
                oppgave(oppgaveId: "123") {
                    sykmeldingId
                }
            }
        """.trimIndent(), "data.oppgave.sykmeldingId"
        )

        oppgave shouldBeEqualTo "555a874f-eaca-49eb-851a-2426a0798b66"
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

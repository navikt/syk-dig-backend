package no.nav.sykdig.nasjonal.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.dokarkiv.DokarkivClient
import no.nav.sykdig.dokarkiv.DocumentService
import no.nav.sykdig.gosys.GosysService
import no.nav.sykdig.gosys.models.NasjonalOppgaveResponse
import no.nav.sykdig.gosys.OppgaveClient
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.models.AvvisSykmeldingRequest
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.nasjonal.models.Veileder
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.mapping.NasjonalSykmeldingMapper
import no.nav.sykdig.nasjonal.util.testDataPapirManuellOppgave
import no.nav.sykdig.pdl.Navn
import no.nav.sykdig.pdl.Person
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.saf.graphql.*
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.shared.utils.getLoggingMeta
import no.nav.sykdig.utenlandsk.models.OppgaveDbModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.support.RestGatewaySupport
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(SpringExtension::class)
class NasjonalOppgaveServiceTest : IntegrationTest() {
    @Autowired
    lateinit var nasjonalOppgaveService: NasjonalOppgaveService
    @Autowired
    lateinit var nasjonalDbService: NasjonalDbService

    val mapper = jacksonObjectMapper()

    @MockitoBean
    lateinit var personService: PersonService

    @MockitoBean
    lateinit var gosysService: GosysService

    @MockitoBean
    lateinit var safJournalpostGraphQlClient: SafJournalpostGraphQlClient

    @MockitoBean
    lateinit var dokarkivClient: DokarkivClient

    @MockitoBean
    lateinit var sykmelderService: SykmelderService

    @MockitoBean
    lateinit var oppgaveClient: OppgaveClient

    @MockitoBean
    lateinit var documentService: DocumentService

    @MockitoBean
    lateinit var nasjonalSykmeldingMapper: NasjonalSykmeldingMapper

    @BeforeEach
    fun setUp() = runBlocking {
        mockJwtAuthentication()
        val mockServer = MockRestServiceServer.createServer(RestGatewaySupport())

        mockServer.expect(requestTo("http://localhost:8081/azureator/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\": \"dummy-token\"}", MediaType.APPLICATION_JSON))

        nasjonalOppgaveRepository.deleteAll()
    }


    @Test
    fun `avvis oppgave blir oppdatert og lagra i DB`() = runBlocking {
        val oppgaveId = "123"
        val request = mapper.writeValueAsString(AvvisSykmeldingRequest(reason = "MANGLENDE_DIAGNOSE"))
        val originalOppgave = nasjonalDbService.saveOppgave(testDataPapirManuellOppgave(123))

        Mockito.`when`(nasjonalSykmeldingMapper.getNavEmail()).thenReturn("navEmail")
        Mockito.`when`(nasjonalSykmeldingMapper.getNavIdent()).thenReturn(Veileder("navIdent"))

        Mockito.`when`(personService.getPerson(anyString(), anyString())).thenReturn(testDataPerson())

        Mockito.`when`(dokarkivClient.oppdaterOgFerdigstillNasjonalJournalpost(
            journalpostId = any(),
            dokumentInfoId = any(),
            pasientFnr = any(),
            sykmeldingId = any(),
            sykmelder = any(),
            loggingMeta = any(),
            navEnhet = any(),
            avvist = any(),
            perioder = any(),
        )).thenReturn(null)

        Mockito.doNothing().`when`(oppgaveClient).ferdigstillOppgave(any(), any())
        Mockito.doNothing().`when`(documentService).updateDocumentTitle(any(), any(), any())
        val loggingMeta = getLoggingMeta("sykmeldingId", testDataOppgaveDbModel("oppgaveId"))
        assertEquals(testDataLoggingMeta(), loggingMeta)
        Mockito.`when`(sykmelderService.getSykmelderForAvvistOppgave(any(),any(),any())).thenReturn(testDataSykmelder())
        Mockito.`when`(safJournalpostGraphQlClient.getJournalpostNasjonal(any())).thenReturn(
            SafQueryJournalpostNasjonal(
                SafJournalpostNasjonal(Journalstatus.MOTTATT)
            )
        )
        Mockito.`when`(oppgaveClient.getNasjonalOppgave(any(), any())).thenReturn(NasjonalOppgaveResponse(prioritet = "", aktivDato = LocalDate.now(), oppgavetype = ""))


        assertTrue(originalOppgave.avvisningsgrunn == null)
        val avvistOppgave = nasjonalOppgaveService.avvisOppgave(oppgaveId, request,  "enhet")
        assertEquals(avvistOppgave.statusCode, HttpStatus.NO_CONTENT)
    }


    private fun testDataOppgaveDbModel(oppgaveId: String): OppgaveDbModel {
        return OppgaveDbModel(
            oppgaveId = oppgaveId,
            fnr = "fnr",
            journalpostId = "jpdId",
            dokumentInfoId = "DokInfoId",
            dokumenter = emptyList(),
            opprettet = OffsetDateTime.now(),
            ferdigstilt = null,
            tilbakeTilGosys = false,
            avvisingsgrunn = null,
            sykmeldingId = UUID.randomUUID(),
            type = "type",
            sykmelding = null,
            endretAv = "sakebehandler",
            timestamp = OffsetDateTime.now(),
            source = "source",
        )
    }

    fun testDataNasjonalManuellOppgaveDAO(
        id: UUID?,
        sykmeldingId: String,
        oppgaveId: Int?,
    ): NasjonalManuellOppgaveDAO {
        return NasjonalManuellOppgaveDAO(
            id = id,
            sykmeldingId = sykmeldingId,
            journalpostId = "123",
            fnr = "fnr",
            aktorId = "aktor",
            dokumentInfoId = "123",
            datoOpprettet = OffsetDateTime.now(),
            oppgaveId = oppgaveId,
            ferdigstilt = false,
            papirSmRegistrering =
            PapirSmRegistering(
                journalpostId = "123",
                oppgaveId = "123",
                fnr = "fnr",
                aktorId = "aktor",
                dokumentInfoId = "123",
                datoOpprettet = OffsetDateTime.now(),
                sykmeldingId = "123",
                syketilfelleStartDato = LocalDate.now(),
                arbeidsgiver = null,
                medisinskVurdering = null,
                skjermesForPasient = null,
                perioder = null,
                prognose = null,
                utdypendeOpplysninger = null,
                tiltakNAV = null,
                tiltakArbeidsplassen = null,
                andreTiltak = null,
                meldingTilNAV = null,
                meldingTilArbeidsgiver = null,
                kontaktMedPasient = null,
                behandletTidspunkt = null,
                behandler = null,
            ),
            utfall = null,
            ferdigstiltAv = null,
            datoFerdigstilt = null,
            avvisningsgrunn = null,
        )
    }

    fun testDataPerson(): Person {
        return Person(
            fnr = "fnr",
            Navn(
                fornavn = "fornavn",
                etternavn =  "etternavn",
                mellomnavn = "mellomnavn"
            ),
            aktorId = "aktorId",
            bostedsadresse = null,
            oppholdsadresse = null,
            fodselsdato = null
        )
    }

    fun testDataSykmelder(): Sykmelder {
        return Sykmelder(
            hprNummer = "123",
            fnr = "fnr",
            aktorId = "aktorId",
            fornavn = "fornavn",
            mellomnavn = "mellomnavn",
            etternavn = "etternavn",
            godkjenninger = emptyList()
        )
    }

    fun mockJwtAuthentication() {
        val jwt = Jwt.withTokenValue("dummy-token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .claim("NAVident", "test-ident")
            .claim("scope", "test-scope")
            .build()
        val authentication = JwtAuthenticationToken(jwt)
        val securityContext = SecurityContextHolder.createEmptyContext()
        securityContext.authentication = authentication
        SecurityContextHolder.setContext(securityContext)
        ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext))
    }

    fun testDataLoggingMeta(): LoggingMeta {
        return LoggingMeta(
            mottakId = "sykmeldingId",
            journalpostId = "jpdId",
            dokumentInfoId = "DokInfoId",
            msgId = "sykmeldingId",
            sykmeldingId = "sykmeldingId",
        )
    }
}

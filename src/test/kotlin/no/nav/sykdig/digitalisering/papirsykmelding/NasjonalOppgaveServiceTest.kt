package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.dokument.DocumentService
import no.nav.sykdig.digitalisering.felles.Adresse
import no.nav.sykdig.digitalisering.felles.Behandler
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.NasjonalOppgaveResponse
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.AvvisSykmeldingRequest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Veileder
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import no.nav.sykdig.model.OppgaveDbModel
import okhttp3.internal.EMPTY_BYTE_ARRAY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
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
import org.springframework.web.client.RestTemplate
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(SpringExtension::class)
class NasjonalOppgaveServiceTest : IntegrationTest() {
    @Autowired
    lateinit var nasjonalOppgaveService: NasjonalOppgaveService

    val mapper = jacksonObjectMapper()

    @MockitoBean
    lateinit var sykdigOppgaveService: SykDigOppgaveService

    @MockitoBean
    lateinit var personService: PersonService

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
    lateinit var nasjonaCommonService: NasjonalCommonService

    @MockitoBean
    lateinit var nasjonalFerdigstillingService: NasjonalFerdigstillingsService

    @Autowired
    @Qualifier("smregisteringRestTemplate")
    private lateinit var restTemplate: RestTemplate

    @BeforeEach
    fun setUp() = runBlocking {
        mockJwtAuthentication()
        val mockServer = MockRestServiceServer.createServer(restTemplate)

        mockServer.expect(requestTo("http://localhost:8081/azureator/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"access_token\": \"dummy-token\"}", MediaType.APPLICATION_JSON))

        nasjonalOppgaveRepository.deleteAll()
    }


    @Test
    fun `avvis oppgave blir oppdatert og lagra i DB`() = runBlocking {
        val oppgaveId = "123"
        val request = mapper.writeValueAsString(AvvisSykmeldingRequest(reason = "MANGLENDE_DIAGNOSE"))
        val originalOppgave = nasjonalOppgaveService.lagreOppgave(testDataPapirManuellOppgave())

        Mockito.`when`(sykdigOppgaveService.getOppgave(anyString())).thenReturn(testDataOppgaveDbModel(oppgaveId))

        Mockito.`when`(nasjonaCommonService.getNavEmail()).thenReturn("navEmail")
        Mockito.`when`(nasjonaCommonService.getNavIdent()).thenReturn(Veileder("navIdent"))

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
        Mockito.`when`(nasjonaCommonService.getLoggingMeta(any(), any())).thenReturn(testDataLoggingMeta())
        Mockito.`when`(sykmelderService.getSykmelderForAvvistOppgave(any(),any(),any())).thenReturn(testDataSykmelder())
        Mockito.`when`(safJournalpostGraphQlClient.getJournalpostM2m(any())).thenReturn(SafQueryJournalpost(SafJournalpost("tittel", null, null, null, emptyList(), null, null)))
        Mockito.`when`(oppgaveClient.getNasjonalOppgave(any(), any())).thenReturn(NasjonalOppgaveResponse(prioritet = "", aktivDato = LocalDate.now(), oppgavetype = ""))


        assertTrue(originalOppgave.avvisningsgrunn == null)
        val avvistOppgave = nasjonalOppgaveService.avvisOppgave(oppgaveId, request,  "enhet", "auth")
        assertEquals(avvistOppgave.statusCode, HttpStatus.NO_CONTENT)
    }


    @Test
    fun `mapToDao der id er null`() {
        val dao = nasjonalOppgaveService.mapToDao(testDataPapirManuellOppgave(), null)

        assertEquals("123", dao.sykmeldingId)
        assertEquals(null, dao.id)
    }

    @Test
    fun `mapToDao der id ikke er null`() {
        val uuid = UUID.randomUUID()
        val dao = nasjonalOppgaveService.mapToDao(testDataPapirManuellOppgave(), uuid)

        assertEquals("123", dao.sykmeldingId)
        assertEquals(uuid, dao.id)
    }

    @Test
    fun `oppgave blir lagret`() = runBlocking {
        val uuid = UUID.randomUUID()
        val dao = testDataNasjonalManuellOppgaveDAO(uuid, "123", 123)
        val oppgave = nasjonalOppgaveService.lagreOppgave(testDataPapirManuellOppgave())

        assertEquals(oppgave.sykmeldingId, dao.sykmeldingId)
        val res = nasjonalOppgaveRepository.findBySykmeldingId(oppgave.sykmeldingId)
        println(res)
    }

    fun testDataPapirManuellOppgave(): PapirManuellOppgave {
        return PapirManuellOppgave(
            sykmeldingId = "123",
            fnr = "fnr",
            oppgaveid = 123,
            pdfPapirSykmelding = EMPTY_BYTE_ARRAY,
            papirSmRegistering =
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
                behandler = Behandler("fornavn", "mellomnavn", "etternavn", "", "", "", null, Adresse(null, null, null, null, null), null),
            ),
            documents = emptyList(),
        )
    }

    private fun testDataOppgaveDbModel(oppgaveId: String): OppgaveDbModel {
        return OppgaveDbModel(
            oppgaveId = oppgaveId.toString(),
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
            datoOpprettet = LocalDateTime.now(),
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
            mottakId = "mottakId",
            journalpostId = "journalpostId",
            dokumentInfoId = "dokumentInfoId",
            msgId = "msgId",
            sykmeldingId = "sykmeldingId",
        )
    }
}

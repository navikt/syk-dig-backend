package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.dokument.DocumentService
import no.nav.sykdig.digitalisering.felles.Adresse
import no.nav.sykdig.digitalisering.felles.Behandler
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
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.utils.createTitleNasjonal
import okhttp3.internal.EMPTY_BYTE_ARRAY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
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

    @MockBean
    lateinit var sykdigOppgaveService: SykDigOppgaveService

    @MockBean
    lateinit var oppgaveSecurityService: OppgaveSecurityService

    @MockBean
    lateinit var personService: PersonService

    @MockBean
    lateinit var safJournalpostGraphQlClient: SafJournalpostGraphQlClient

    @MockBean
    lateinit var dokarkivClient: DokarkivClient

    @MockBean
    lateinit var sykmelderService: SykmelderService

    @MockBean
    lateinit var oppgaveClient: OppgaveClient

    @MockBean
    lateinit var documentService: DocumentService

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
    fun `avvis oppgave blir oppdatert og lagra i DB`() {
        val oppgaveId = 123
        val request = mapper.writeValueAsString(AvvisSykmeldingRequest(reason = "MANGLENDE_DIAGNOSE"))
        val originalOppgave = nasjonalOppgaveService.lagreOppgave(testDataPapirManuellOppgave())

        Mockito.`when`(sykdigOppgaveService.getOppgave(anyString())).thenReturn(testDataOppgaveDbModel(oppgaveId))
        Mockito.`when`(oppgaveSecurityService.getNavIdent()).thenReturn(Veileder("veilederIdent"))
        Mockito.`when`(oppgaveSecurityService.getNavEmail()).thenReturn("NavEmail")
        Mockito.`when`(personService.getPerson(anyString(), anyString())).thenReturn(testDataPerson())
        Mockito.`when`(safJournalpostGraphQlClient.getJournalpost(anyString())).thenReturn(SafQueryJournalpost(null))

        Mockito.`when`(safJournalpostGraphQlClient.erFerdigstilt(org.mockito.kotlin.any())).thenReturn(false)
        Mockito.`when`(dokarkivClient.oppdaterOgFerdigstillNasjonalJournalpost(
            journalpostId = org.mockito.kotlin.any(),
            dokumentInfoId = org.mockito.kotlin.any(),
            pasientFnr = org.mockito.kotlin.any(),
            sykmeldingId = org.mockito.kotlin.any(),
            sykmelder = org.mockito.kotlin.any(),
            loggingMeta = org.mockito.kotlin.any(),
            navEnhet = org.mockito.kotlin.any(),
            avvist = org.mockito.kotlin.any(),
            perioder = org.mockito.kotlin.any(),
        )).thenReturn(null)

        Mockito.`when`(sykmelderService.getSykmelder(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(testDataSykmelder())
        Mockito.doNothing().`when`(oppgaveClient).ferdigstillOppgave(org.mockito.kotlin.any(), org.mockito.kotlin.any())
        Mockito.doNothing().`when`(documentService).updateDocumentTitle(org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any())

        assertTrue(originalOppgave.avvisningsgrunn == null)
        val avvistOppgave = nasjonalOppgaveService.avvisOppgave(oppgaveId, request, "auth streng", "enhet")
        assertEquals(testDataNasjonalManuellOppgaveDAO(null, "456", oppgaveId).oppgaveId, avvistOppgave.body?.oppgaveId ?: 123)
        assertTrue(avvistOppgave.body?.avvisningsgrunn == "MANGLENDE_DIAGNOSE")
        assertEquals(avvistOppgave.body?.id, originalOppgave.id)

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
            oppgaveId = 123,
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

    private fun testDataOppgaveDbModel(oppgaveId: Int): OppgaveDbModel {
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
}

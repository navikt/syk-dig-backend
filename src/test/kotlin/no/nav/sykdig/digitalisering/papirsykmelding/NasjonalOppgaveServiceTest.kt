package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import okhttp3.internal.EMPTY_BYTE_ARRAY
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(SpringExtension::class)
class NasjonalOppgaveServiceTest : IntegrationTest() {
    @Autowired
    lateinit var nasjonalOppgaveService: NasjonalOppgaveService

    @Autowired
    lateinit var sykDigOppgaveService: SykDigOppgaveService

    @BeforeEach
    fun setUp() {
        nasjonalOppgaveRepository.deleteAll()
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
    fun `mapFromDao der id ikke er null`() {
        val uuid = UUID.randomUUID()
        val papirSykmelding = nasjonalOppgaveService.mapFromDao(testDataNasjonalManuellOppgaveDAO(uuid, "123"))

        assertEquals("123", papirSykmelding.sykmeldingId)
        assertEquals("fnr", papirSykmelding.fnr)
    }

    @Test
    fun `oppgave isPresent`() {
        val uuid = UUID.randomUUID()
        val dao = testDataNasjonalManuellOppgaveDAO(uuid, "123")
        val oppgave = nasjonalOppgaveService.lagreOppgave(testDataPapirManuellOppgave())

        assertEquals(oppgave.sykmeldingId, dao.sykmeldingId)
    }

    @Test
    fun `hent ferdigstilt oppgave`() {
        val lagretOppgave = nasjonalOppgaveService.lagreOppgave(testDataPapirManuellOppgave())
        sykDigOppgaveService.ferdigstillOppgave()
        val oppgave = nasjonalOppgaveService.hentFerdigstiltOppgave("123")
        assertNotNull(lagretOppgave)
        assertNotNull(oppgave)
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
                    behandler = null,
                ),
            documents = emptyList(),
        )
    }

    fun testDataNasjonalManuellOppgaveDAO(
        id: UUID?,
        sykmeldingId: String,
    ): NasjonalManuellOppgaveDAO {
        return NasjonalManuellOppgaveDAO(
            id = id,
            sykmeldingId = sykmeldingId,
            journalpostId = "123",
            fnr = "fnr",
            aktorId = "aktor",
            dokumentInfoId = "123",
            datoOpprettet = LocalDateTime.now(),
            oppgaveId = 123,
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

    fun testDataNasjonalManuellOppgaveFerdigstiltDAO(
        id: UUID?,
        sykmeldingId: String,
    ): NasjonalManuellOppgaveDAO {
        return NasjonalManuellOppgaveDAO(
            id = id,
            sykmeldingId = sykmeldingId,
            journalpostId = "456",
            fnr = "fnr",
            aktorId = "aktor",
            dokumentInfoId = "123",
            datoOpprettet = LocalDateTime.now(),
            oppgaveId = 123,
            ferdigstilt = true,
            papirSmRegistrering =
            PapirSmRegistering(
                journalpostId = "123",
                oppgaveId = "123",
                fnr = "fnr",
                aktorId = "aktor",
                dokumentInfoId = "123",
                datoOpprettet = OffsetDateTime.now(),
                sykmeldingId = "456",
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
}

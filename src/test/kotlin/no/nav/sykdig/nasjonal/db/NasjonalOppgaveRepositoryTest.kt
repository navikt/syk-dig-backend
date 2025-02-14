package no.nav.sykdig.nasjonal.db

import kotlinx.coroutines.runBlocking
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class NasjonalOppgaveRepositoryTest : IntegrationTest() {
    @Test
    fun `given New NasjonalOppgave opprett og hent`() = runBlocking {
        val savedOppgave = nasjonalOppgaveRepository.save(testData(null, "123"))
        val retrievedOppgave = nasjonalOppgaveRepository.findBySykmeldingId(savedOppgave.sykmeldingId)
        Assertions.assertNotNull(retrievedOppgave)
        assertEquals(savedOppgave.sykmeldingId, retrievedOppgave?.sykmeldingId)
    }

    @Test
    fun `insert two instances with same sykmeldingId`() = runBlocking {
        nasjonalOppgaveRepository.save(testData(null, "1"))
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId("1")
        nasjonalOppgaveRepository.save(testData(eksisterendeOppgave?.id, "1"))
        val retrievedOppgave = nasjonalOppgaveRepository.findAll()
        assertEquals(1, retrievedOppgave.count())
    }

    @Test
    fun `insert two instances with unique id`() = runBlocking {
        nasjonalOppgaveRepository.save(testData(null, "3"))
        nasjonalOppgaveRepository.save(testData(null, "4"))
        val retrievedOppgave = nasjonalOppgaveRepository.findAll()
        assertEquals(2, retrievedOppgave.count())
    }

    @Test
    fun `delete sykmelding by id`() = runBlocking {
        nasjonalOppgaveRepository.save(testData(null, "3"))
        nasjonalOppgaveRepository.save(testData(null, "4"))
        val id = nasjonalOppgaveRepository.findBySykmeldingId("3")?.id
        nasjonalOppgaveRepository.deleteById(id)
        assertEquals(null, nasjonalOppgaveRepository.findBySykmeldingId("3"))
        assertEquals("4", nasjonalOppgaveRepository.findBySykmeldingId("4")?.sykmeldingId)
    }

    @BeforeEach
    fun setup() = runBlocking {
        nasjonalOppgaveRepository.deleteAll()
    }

    fun testData(
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
            datoOpprettet = OffsetDateTime.now(),
            oppgaveId = 123,
            ferdigstilt = false,
            papirSmRegistrering =
                PapirSmRegistering(
                    journalpostId = "123",
                    oppgaveId = "123",
                    fnr = "fnr",
                    aktorId = "aktor",
                    dokumentInfoId = "123",
                    datoOpprettet = LocalDateTime.now(),
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
}

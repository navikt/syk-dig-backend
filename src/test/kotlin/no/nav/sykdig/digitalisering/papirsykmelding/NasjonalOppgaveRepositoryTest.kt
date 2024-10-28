package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class NasjonalOppgaveRepositoryTest : IntegrationTest() {
    @Test
    fun `given New NasjonalOppgave opprett og hent`() {
        val savedOppgave = nasjonalOppgaveRepository.save(testData("123"))
        val retrievedOppgave = nasjonalOppgaveRepository.findById(savedOppgave.sykmeldingId)
        Assertions.assertTrue(retrievedOppgave.isPresent)
        assertEquals(savedOppgave.sykmeldingId, retrievedOppgave.get().sykmeldingId)
    }

    @Test
    fun `insert two instances with same sykmeldingId`() {
        nasjonalOppgaveRepository.save(testData("1"))
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId("1")
        nasjonalOppgaveRepository.save(testData("1"))
        val retrievedOppgave = nasjonalOppgaveRepository.findAll()
        assertEquals(1, retrievedOppgave.count())
    }

    fun testData(sykmeldingId: String): NasjonalManuellOppgaveDAO {
        return NasjonalManuellOppgaveDAO(
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
}

package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.objectMapper
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Test
import org.postgresql.util.PGobject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

class NasjonalOppgaveRepositoryTest : IntegrationTest() {
    @Test
    fun `given New NasjonalOppgave`() {
        val oppgave = nasjonalOppgaveRepository.save(testData())
        assertEquals("noe", oppgave)
    }

    fun testData(): NasjonalManuellOppgaveDAO {
        val objectMapper = jacksonObjectMapper()
        objectMapper.registerModules(JavaTimeModule())
        return NasjonalManuellOppgaveDAO(
            sykmeldingId = "123",
            journalpostId = "123",
            fnr = "fnr",
            aktorId = "aktor",
            dokumentInfoId = "123",
            datoOpprettet = LocalDateTime.now(),
            oppgaveId = 123,
            ferdigstilt = false,
            papirSmRegistrering =
                objectMapper.writeValueAsString(
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
                ),
            utfall = null,
            ferdigstiltAv = null,
            datoFerdigstilt = null,
            avvisningsgrunn = null,
        )
    }

    fun <T> T.toPGObject() =
        PGobject().also {
            it.type = "json"
            it.value = objectMapper.writeValueAsString(this)
        }
}

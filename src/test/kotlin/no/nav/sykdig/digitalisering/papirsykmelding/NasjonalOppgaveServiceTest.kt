package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import okhttp3.internal.EMPTY_BYTE_ARRAY
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDate
import java.time.OffsetDateTime

class NasjonalOppgaveServiceTest : IntegrationTest() {

    @MockBean
    lateinit var nasjonalOppgaveService: NasjonalOppgaveService


    @Test
    fun `insert one oppgave`() {
        val oppgave = nasjonalOppgaveService.lagreOppgave(testPapirManuellOppgave("1"))
        assertEquals(1, oppgave.id)
        assertEquals("1", oppgave.sykmeldingId)
    }

    fun testPapirManuellOppgave(sykmeldindId: String): PapirManuellOppgave {
        return PapirManuellOppgave(
            fnr = null,
            sykmeldingId = sykmeldindId,
            oppgaveid = 1,
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
}

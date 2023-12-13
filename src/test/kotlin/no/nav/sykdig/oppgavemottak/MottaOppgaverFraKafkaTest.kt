package no.nav.sykdig.oppgavemottak

import no.nav.sykdig.FellesTestOppsett
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID

class MottaOppgaverFraKafkaTest : FellesTestOppsett() {
    @Autowired
    lateinit var mottaOppgaverFraKafka: MottaOppgaverFraKafka

    @Test
    fun testReposistory() {
        val sykmeldingId = UUID.randomUUID().toString()
        val digitaliseringsoppgaveKafka =
            DigitaliseringsoppgaveKafka(
                oppgaveId = "1234",
                fnr = "12345678910",
                journalpostId = "11",
                dokumentInfoId = null,
                type = "UTLAND",
                dokumenter = emptyList(),
            )

        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)

        val oppgave = oppgaveRepository.getOppgave("1234")

        assertEquals("1234", oppgave!!.oppgaveId)
        assertEquals("12345678910", oppgave.fnr)
        assertEquals("11", oppgave.journalpostId)
        assertEquals(null, oppgave.dokumentInfoId)
        assertEquals(LocalDate.now(), oppgave.opprettet.toLocalDate())
        assertEquals(null, oppgave.ferdigstilt)
        assertEquals(sykmeldingId, oppgave.sykmeldingId.toString())
        assertEquals("UTLAND", oppgave.type)
        assertEquals(null, oppgave.sykmelding)
        assertEquals("syk-dig-backend", oppgave.endretAv)
        assertEquals(LocalDate.now(), oppgave.timestamp.toLocalDate())
    }

    @Test
    fun testInsertDuplicateOppgave() {
        val sykmeldingId = UUID.randomUUID().toString()
        val digitaliseringsoppgaveKafka =
            DigitaliseringsoppgaveKafka(
                oppgaveId = "1234",
                fnr = "12345678910",
                journalpostId = "11",
                dokumentInfoId = null,
                type = "UTLAND",
                dokumenter = emptyList(),
            )

        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)
        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)
    }

    @Test
    fun testInsertWithMultipleDocuments() {
        val sykmeldingId = UUID.randomUUID().toString()
        val digitaliseringsoppgaveKafka =
            DigitaliseringsoppgaveKafka(
                oppgaveId = "12345",
                fnr = "12345678910",
                journalpostId = "11",
                dokumentInfoId = null,
                type = "UTLAND",
                dokumenter =
                    listOf(
                        DokumentKafka(
                            tittel = "tittel",
                            dokumentInfoId = "id",
                        ),
                        DokumentKafka(
                            tittel = "tittel-2",
                            dokumentInfoId = "id-2",
                        ),
                    ),
            )

        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)
        val lagretOppdave = oppgaveRepository.getOppgave("12345")
        assertEquals(2, lagretOppdave?.dokumenter?.size)
    }
}

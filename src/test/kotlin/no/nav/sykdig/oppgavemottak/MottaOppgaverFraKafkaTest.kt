package no.nav.sykdig.oppgavemottak

import no.nav.sykdig.FellesTestOppsett
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals

class MottaOppgaverFraKafkaTest : FellesTestOppsett() {
    @Autowired
    lateinit var mottaOppgaverFraKafka: MottaOppgaverFraKafka

    @Test
    fun testReposistory() {
        val sykmeldingId = UUID.randomUUID().toString()
        val digitaliseringsoppgaveKafka = DigitaliseringsoppgaveKafka(
            oppgaveId = "1234",
            fnr = "12345678910",
            journalpostId = "11",
            dokumentInfoId = null,
            type = "UTLAND"
        )

        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)

        val oppgave = oppgaveRepository.getOppgave("1234")

        assertEquals(null, oppgave)
        assertEquals("1234", oppgave!!.oppgaveId)
        assertEquals("12345678910", oppgave.fnr)
        assertEquals("11", oppgave.journalpostId)
        assertEquals(null, oppgave.dokumentInfoId)
        assertEquals(LocalDate.now(), oppgave.opprettet.toLocalDate())
        assertEquals(null, oppgave.ferdigstilt)
        assertEquals(sykmeldingId,  oppgave.sykmeldingId.toString())
        assertEquals("UTLAND",  oppgave.type)
        assertEquals(null, oppgave.sykmelding)
        assertEquals("syk-dig-backend", oppgave.endretAv)
        assertEquals(LocalDate.now(), oppgave.timestamp.toLocalDate())
    }

    @Test
    fun testInsertDuplicateOppgave() {
        val sykmeldingId = UUID.randomUUID().toString()
        val digitaliseringsoppgaveKafka = DigitaliseringsoppgaveKafka(
            oppgaveId = "1234",
            fnr = "12345678910",
            journalpostId = "11",
            dokumentInfoId = null,
            type = "UTLAND"
        )

        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)
        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)
    }
}

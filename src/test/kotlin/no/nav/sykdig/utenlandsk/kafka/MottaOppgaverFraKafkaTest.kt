package no.nav.sykdig.utenlandsk.kafka

import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import no.nav.sykdig.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class MottaOppgaverFraKafkaTest : IntegrationTest() {
    @Autowired lateinit var mottaOppgaverFraKafka: MottaOppgaverFraKafka

    @Test
    fun testReposistory() {
        val sykmeldingId = UUID.randomUUID().toString()
        val digitaliseringsoppgaveScanning =
            DigitaliseringsoppgaveScanning(
                oppgaveId = "1234",
                fnr = "12345678910",
                journalpostId = "11",
                dokumentInfoId = null,
                type = "UTLAND",
                dokumenter = emptyList(),
            )

        mottaOppgaverFraKafka.lagre(digitaliseringsoppgaveScanning, sykmeldingId)

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
    fun testInsertWithMultipleDocuments() {
        val sykmeldingId = UUID.randomUUID().toString()
        val digitaliseringsoppgaveScanning =
            DigitaliseringsoppgaveScanning(
                oppgaveId = "12345",
                fnr = "12345678910",
                journalpostId = "11",
                dokumentInfoId = null,
                type = "UTLAND",
                dokumenter =
                    listOf(
                        DokumentMedTittel(tittel = "tittel", dokumentInfoId = "id"),
                        DokumentMedTittel(tittel = "tittel-2", dokumentInfoId = "id-2"),
                    ),
            )

        mottaOppgaverFraKafka.lagre(digitaliseringsoppgaveScanning, sykmeldingId)
        val lagretOppdave = oppgaveRepository.getOppgave("12345")
        assertEquals(2, lagretOppdave?.dokumenter?.size)
    }
}

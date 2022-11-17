package no.nav.sykdig.oppgavemottak

import no.nav.sykdig.FellesTestOppsett
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
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
        val digitaliseringsoppgaveKafka = DigitaliseringsoppgaveKafka(
            oppgaveId = "1234",
            fnr = "12345678910",
            journalpostId = "11",
            dokumentInfoId = null,
            type = "UTLAND"
        )

        mottaOppgaverFraKafka.lagre(sykmeldingId, digitaliseringsoppgaveKafka)

        val oppgave = oppgaveRepository.getOppgave("1234")

        oppgave shouldNotBeEqualTo null
        oppgave!!.oppgaveId shouldBeEqualTo "1234"
        oppgave.fnr shouldBeEqualTo "12345678910"
        oppgave.journalpostId shouldBeEqualTo "11"
        oppgave.dokumentInfoId shouldBeEqualTo null
        oppgave.opprettet.toLocalDate() shouldBeEqualTo LocalDate.now()
        oppgave.ferdigstilt shouldBeEqualTo null
        oppgave.sykmeldingId.toString() shouldBeEqualTo sykmeldingId
        oppgave.type shouldBeEqualTo "UTLAND"
        oppgave.sykmelding shouldBeEqualTo null
        oppgave.endretAv shouldBeEqualTo "syk-dig-backend"
        oppgave.timestamp.toLocalDate() shouldBeEqualTo LocalDate.now()
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

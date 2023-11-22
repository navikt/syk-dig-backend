package no.nav.sykdig.digitalisering.sykmelding.db

import no.nav.sykdig.FellesTestOppsett
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException

class JournalpostSykmeldingRepositoryTest : FellesTestOppsett() {

    @Autowired
    lateinit var journalpostSykmeldingRepository: JournalpostSykmeldingRepository

    @Test
    fun shouldGetNullWhenNotCreated() {
        val journalpostSykmelding = journalpostSykmeldingRepository.getJournalpostSykmelding("123")
        assertEquals(null, journalpostSykmelding)
    }

    @Test
    fun insertJournalpostSykmelding() {
        val inserted = journalpostSykmeldingRepository.insertJournalpostId("1234")
        assertEquals(1, inserted)
    }

    @Test
    fun insertJournalpostTwoTimesShouldFail() {
        val inserted = journalpostSykmeldingRepository.insertJournalpostId("12345")
        assertEquals(1, inserted)
        assertThrows<DuplicateKeyException> { journalpostSykmeldingRepository.insertJournalpostId("12345") }
    }

    @Test
    fun insertAndGetJournalpost() {
        val inserted = journalpostSykmeldingRepository.insertJournalpostId("123456")
        assertEquals(1, inserted)
        val journalpostSykmelding = journalpostSykmeldingRepository.getJournalpostSykmelding("123456")
        assertEquals("123456", journalpostSykmelding?.journalpostId)
        assertNotNull(journalpostSykmelding?.created)
    }
}

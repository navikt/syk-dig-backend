package no.nav.sykdig.utenlandsk.db

import no.nav.sykdig.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class JournalpostSDSykmeldingRepositoryTest : IntegrationTest() {
    @Autowired lateinit var journalpostSykmeldingRepository: JournalpostSykmeldingRepository

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
    fun insertAndGetJournalpost() {
        val inserted = journalpostSykmeldingRepository.insertJournalpostId("123456")
        assertEquals(1, inserted)
        val journalpostSykmelding =
            journalpostSykmeldingRepository.getJournalpostSykmelding("123456")
        assertEquals("123456", journalpostSykmelding?.journalpostId)
        assertNotNull(journalpostSykmelding?.created)
    }
}

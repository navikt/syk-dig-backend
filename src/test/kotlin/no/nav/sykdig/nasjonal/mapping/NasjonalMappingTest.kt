package no.nav.sykdig.nasjonal.mapping

import java.util.*
import no.nav.sykdig.digitalisering.papirsykmelding.mapToDaoOppgave
import no.nav.sykdig.nasjonal.util.testDataPapirManuellOppgave
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NasjonalMappingTest {

    @Test
    fun `mapToDao der id er null`() {
        val dao = mapToDaoOppgave(testDataPapirManuellOppgave(123), null)

        assertEquals("123", dao.sykmeldingId)
        assertEquals(null, dao.id)
    }

    @Test
    fun `mapToDao der id ikke er null`() {
        val uuid = UUID.randomUUID()
        val dao = mapToDaoOppgave(testDataPapirManuellOppgave(123), uuid)

        assertEquals("123", dao.sykmeldingId)
        assertEquals(uuid, dao.id)
    }
}

package no.nav.sykdig.nasjonal.services

import java.util.*
import kotlin.test.assertNotNull
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.nasjonal.util.testDataPapirManuellOppgave
import no.nav.sykdig.shared.Periode
import no.nav.sykdig.utils.TestHelper.Companion.januar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class NasjonalDbServiceTest : IntegrationTest() {

    @Autowired lateinit var nasjonalDbService: NasjonalDbService

    @BeforeEach
    fun setup() {
        nasjonalOppgaveRepository.deleteAll()
    }

    @Test
    fun `oppgave blir lagret`() {
        nasjonalDbService.saveOppgave(testDataPapirManuellOppgave(123))
        val oppgave = nasjonalDbService.getOppgaveByOppgaveId("123")
        assertNotNull(oppgave)
    }

    @Test
    fun `oppgave som eksisterer blir endret`() {
        nasjonalDbService.saveOppgave(testDataPapirManuellOppgave(123))
        val periode =
            listOf(Periode(1.januar(2023), 24.januar(2023), null, null, null, null, false))
        nasjonalDbService.saveOppgave(testDataPapirManuellOppgave(123, perioder = periode))
        val oppgave = nasjonalDbService.getOppgaveByOppgaveId("123")
        assertEquals(periode, oppgave?.papirSmRegistrering?.perioder)
    }
}

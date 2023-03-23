package no.nav.sykdig.digitalisering

import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.digitalisering.ferdigstilling.GosysService
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.regelvalidering.RegelvalideringService
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.metrics.MetricRegister
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean

@SpringBootTest(classes = [DigitaliseringsoppgaveService::class])
class DigitaliseringsoppgaveServiceTest : FellesTestOppsett() {
    @MockBean
    lateinit var sykDigOppgaveService: SykDigOppgaveService

    @MockBean
    lateinit var gosysService: GosysService

    @MockBean
    lateinit var personService: PersonService

    @MockBean
    lateinit var metricRegister: MetricRegister

    @MockBean
    lateinit var regelvalideringService: RegelvalideringService

    @Autowired
    lateinit var digitaliseringsoppgaveService: DigitaliseringsoppgaveService

    @Test
    fun testAvvis() {
       val sykDigOppgave = digitaliseringsoppgaveService.avvisOppgave("1", "Z123456", "Z123456@trygdeetaten.no", Avvisingsgrunn.MANGLENDE_DIAGNOSE)
    }
}

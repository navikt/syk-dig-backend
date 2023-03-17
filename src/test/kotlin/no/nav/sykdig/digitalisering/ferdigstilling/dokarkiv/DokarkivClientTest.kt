package no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv

import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.SykDigBackendApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [SykDigBackendApplication::class])
class DokarkivClientTest : FellesTestOppsett() {
    @Test
    fun oppdaterOgFerdigstillJournalpost() {

    }
}

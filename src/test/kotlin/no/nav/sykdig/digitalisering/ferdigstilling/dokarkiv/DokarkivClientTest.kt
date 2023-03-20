package no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv

import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.SykDigBackendApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mock
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.client.RestTemplate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [SykDigBackendApplication::class])
class DokarkivClientTest : FellesTestOppsett() {

    @Mock
    lateinit var dokarkivRestTemplate: RestTemplate

    @Test
    fun `Should find Bahamas as country name`() {
        val dokarkivClient = DokarkivClient(url = "localhost", dokarkivRestTemplate = dokarkivRestTemplate)

        val landAlpha3 = "BHS"

        val landName = dokarkivClient.findCountryName(landAlpha3)

        assertEquals("Bahamas", landName)
    }

    @Test
    fun `Should find bs as country alpha2`() {
        val dokarkivClient = DokarkivClient(url = "localhost", dokarkivRestTemplate = dokarkivRestTemplate)

        val landAlpha3 = "BHS"

        val landAlpha2 = dokarkivClient.mapFromAlpha3Toalpha2(landAlpha3)

        assertEquals("bs", landAlpha2)
    }
}

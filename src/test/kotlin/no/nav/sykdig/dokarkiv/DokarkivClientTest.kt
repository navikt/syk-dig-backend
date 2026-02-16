package no.nav.sykdig.dokarkiv

import no.nav.sykdig.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.springframework.web.client.RestTemplate

class DokarkivClientTest : IntegrationTest() {
    @Mock lateinit var dokarkivRestTemplate: RestTemplate

    @Test
    fun `Should find Bahamas as country name`() {
        val dokarkivClient =
            DokarkivClient(url = "localhost", dokarkivRestTemplate = dokarkivRestTemplate)

        val landAlpha3 = "BHS"

        val landName = dokarkivClient.findCountryName(landAlpha3)

        assertEquals("Bahamas", landName)
    }

    @Test
    fun `Should find bs as country alpha2`() {
        val dokarkivClient =
            DokarkivClient(url = "localhost", dokarkivRestTemplate = dokarkivRestTemplate)

        val landAlpha3 = "BHS"

        val landAlpha2 = dokarkivClient.mapFromAlpha3Toalpha2(landAlpha3)

        assertEquals("bs", landAlpha2)
    }
}

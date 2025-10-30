package no.nav.sykdig.utenlandsk.poststed

import no.nav.sykdig.shared.objectMapper
import no.nav.sykdig.utenlandsk.poststed.client.GetKodeverkKoderBetydningerResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KodeverkResponseMappingTest {
    @Test
    fun kodeverkresponseMappesRiktig() {
        val kodeverkrespons =
            objectMapper.readValue(
                KodeverkResponseMappingTest::class
                    .java
                    .getResourceAsStream("/kodeverkrespons.json"),
                GetKodeverkKoderBetydningerResponse::class.java,
            )

        val postinformasjonListe = kodeverkrespons.toPostInformasjonListe()
        assertEquals("ULEFOSS", postinformasjonListe.find { it.postnummer == "3831" }?.poststed)
    }
}

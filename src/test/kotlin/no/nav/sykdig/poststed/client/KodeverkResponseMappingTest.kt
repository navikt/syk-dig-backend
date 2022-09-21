package no.nav.sykdig.poststed.client

import no.nav.sykdig.objectMapper
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class KodeverkResponseMappingTest {

    @Test
    fun kodeverkresponseMappesRiktig() {
        val kodeverkrespons = objectMapper.readValue(
            KodeverkResponseMappingTest::class.java.getResourceAsStream("/kodeverkrespons.json"),
            GetKodeverkKoderBetydningerResponse::class.java
        )

        val postinformasjonListe = kodeverkrespons.toPostInformasjonListe()

        postinformasjonListe.find { it.postnummer == "3831" }?.poststed shouldBeEqualTo "ULEFOSS"
    }
}

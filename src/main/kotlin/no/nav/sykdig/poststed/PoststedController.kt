package no.nav.sykdig.poststed

import no.nav.sykdig.applog
import no.nav.sykdig.db.PoststedRepository
import no.nav.sykdig.poststed.client.KodeverkClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class PoststedController(
    val kodeverkClient: KodeverkClient,
    val poststedRepository: PoststedRepository,
) {
    val log = applog()

    @GetMapping("/internal/postnummer")
    fun updatePostnummer() {
        val callId = UUID.randomUUID()
        log.info("Oppdaterer database med postnummer og poststed, $callId")
        val postInformasjonListe = kodeverkClient.hentKodeverk(callId)
        poststedRepository.oppdaterPoststed(postInformasjonListe, callId)
        log.info("Ferdig med å oppdatere poststed i database, $callId")
    }
}

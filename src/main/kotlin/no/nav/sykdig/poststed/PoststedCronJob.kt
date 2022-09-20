package no.nav.sykdig.poststed

import no.nav.sykdig.db.PoststedRepository
import no.nav.sykdig.logger
import no.nav.sykdig.poststed.client.KodeverkClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PoststedCronJob(
    val leaderElection: LeaderElection,
    val kodeverkClient: KodeverkClient,
    val poststedRepository: PoststedRepository
) {
    val log = logger()

    @Scheduled(cron = "0 6 * * *")
    fun run() {
        if (leaderElection.isLeader()) {
            val callId = UUID.randomUUID()
            log.info("Oppdaterer database med postnummer og poststed, $callId")
            val postInformasjonListe = kodeverkClient.hentKodeverk(callId)
            poststedRepository.oppdaterPoststed(postInformasjonListe, callId)
            log.info("Ferdig med å oppdatere poststed i database, $callId")
        } else {
            log.info("Kjører ikke poststed-oppdatering siden denne podden ikke er leader")
        }
    }
}

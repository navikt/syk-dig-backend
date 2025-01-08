package no.nav.sykdig.utenlandsk.poststed

import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.db.PoststedRepository
import no.nav.sykdig.utenlandsk.poststed.client.KodeverkClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PoststedCronJob(
    val leaderElection: LeaderElection,
    val kodeverkClient: KodeverkClient,
    val poststedRepository: PoststedRepository,
) {
    val log = applog()

    @Scheduled(cron = "0 0 6 * * *")
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

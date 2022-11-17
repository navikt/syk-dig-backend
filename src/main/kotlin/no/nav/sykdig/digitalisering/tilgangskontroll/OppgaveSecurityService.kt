package no.nav.sykdig.digitalisering.tilgangskontroll

import no.nav.sykdig.digitalisering.OppgaveService
import no.nav.sykdig.logger
import org.springframework.stereotype.Service

@Service
class OppgaveSecurityService(val syfoTilgangskontrollOboClient: SyfoTilgangskontrollOboClient, val oppgaveService: OppgaveService) {

    companion object {
        private val log = logger()
    }
    fun hasAccessToOppgave(oppgaveId: String): Boolean {
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        if (!syfoTilgangskontrollOboClient.sjekkTilgangVeileder(oppgave.fnr)) {
            log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
            return false
        }
        log.info("Innlogget bruker har tilgang til oppgave med id $oppgaveId")
        return true
    }
}

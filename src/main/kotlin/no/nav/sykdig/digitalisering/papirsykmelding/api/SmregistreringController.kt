package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.securelog
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SmregistreringController(
    private val smregistreringClient: SmregistreringClient,
) {
    val securelog = securelog()
    val log = applog()

    // @PostAuthorize("@oppgaveSecurityService.hasAccessToOppgave(oppgaveId)")
    @PostMapping("/api/v1/proxy/oppgave/{oppgaveId}/avvis")
    fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ): String {
        log.info("avviser oppgave med id $oppgaveId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.postSmregistreringRequest(token, oppgaveId, "avvis")
        securelog.info(res)
        return res
    }

    // @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(oppgaveId)")
    @GetMapping("/api/v1/proxy/oppgave/{oppgaveId}")
    @ResponseBody
    fun getPapirsykmeldingManuellOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ): PapirManuellOppgave {
        log.info("henter oppgave med id $oppgaveId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getSmregistreringRequest(token, oppgaveId)
        securelog.info(res.body.toString())
        return res.body!!
    }
}

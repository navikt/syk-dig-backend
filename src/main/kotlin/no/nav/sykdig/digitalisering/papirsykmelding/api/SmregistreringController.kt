package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.securelog
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class SmregistreringController(
    private val smregistreringClient: SmregistreringClient,
) {
    val securelog = securelog()

    @PostAuthorize("@oppgaveSecurityService.hasAccessToOppgave(oppgaveId)")
    @PostMapping("/api/v1/proxy/oppgave/{oppgaveId}/avvis")
    fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ) {
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.postSmregistreringRequest(token, oppgaveId)
        securelog.info(res.toString())
        return res
    }

//    @GetMapping("/api/v1/proxy/oppgave/{oppgaveId}/hent")
//    fun foo() {
//        return "bar"
//    }
}

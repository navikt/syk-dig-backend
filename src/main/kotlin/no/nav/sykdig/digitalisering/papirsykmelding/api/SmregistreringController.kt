package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SmregistreringController(
    private val smregistreringClient: SmregistreringClient,
) {
    val log = applog()

    @PostMapping("/api/v1/proxy/oppgave/{oppgaveId}/avvis")
    fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") enhet: String,
        @RequestBody avvisSykmeldingRequest: String,
    ): ResponseEntity<Void> {
        log.info("avviser oppgave med id $oppgaveId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        smregistreringClient.postSmregistreringRequest(token, oppgaveId, "avvis", enhet, avvisSykmeldingRequest)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/v1/proxy/oppgave/{oppgaveid}")
    @ResponseBody
    fun getPapirsykmeldingManuellOppgave(
        @PathVariable oppgaveid: String,
        @RequestHeader("Authorization") authorization: String,
    ): PapirManuellOppgave {
        log.info("henter oppgave med id $oppgaveid gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getSmregistreringRequest(token, oppgaveid)
        return res
    }

    @GetMapping("/api/v1/proxy/pasient")
    @ResponseBody
    fun getPasientNavn(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Pasient-Fnr") fnr: String,
    ): PasientNavn {
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getPasientNavnRequest(token, fnr)
        return res
    }

    @GetMapping("/api/v1/proxy/sykmelder/{hprNummer}")
    @ResponseBody
    fun getSykmelder(
        @PathVariable hprNummer: String,
        @RequestHeader("Authorization") authorization: String,
    ): Sykmelder {
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getSykmelderRequest(token, hprNummer)
        return res
    }
}

package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/proxy")
class SmregistreringController(
    private val smregistreringClient: SmregistreringClient,
) {
    val log = applog()

    @PostMapping("/oppgave/{oppgaveId}/avvis")
    fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody avvisSykmeldingRequest: String,
    ): ResponseEntity<Void> {
        log.info("avviser oppgave med id $oppgaveId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        smregistreringClient.postSmregistreringRequest(token, oppgaveId, "avvis", navEnhet, avvisSykmeldingRequest)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/oppgave/{oppgaveid}")
    @ResponseBody
    fun getPapirsykmeldingManuellOppgave(
        @PathVariable oppgaveid: String,
        @RequestHeader("Authorization") authorization: String,
    ): PapirManuellOppgave {
        log.info("henter oppgave med id $oppgaveid gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getOppgaveRequest(token, oppgaveid)
        return res
    }

    @GetMapping("/pasient")
    @ResponseBody
    fun getPasientNavn(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Pasient-Fnr") fnr: String,
    ): PasientNavn {
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getPasientNavnRequest(token, fnr)
        return res
    }

    @GetMapping("/sykmelder/{hprNummer}")
    @ResponseBody
    fun getSykmelder(
        @PathVariable hprNummer: String,
        @RequestHeader("Authorization") authorization: String,
    ): Sykmelder {
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getSykmelderRequest(token, hprNummer)
        return res
    }

    @PostMapping("/oppgave/{oppgaveId}/send")
    fun sendOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<Void> {
        val token = authorization.removePrefix("Bearer ")
        smregistreringClient.postSendOppgaveRequest(token, oppgaveId, navEnhet, papirSykmelding)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/sykmelding/{sykmeldingId}/ferdigstilt")
    @ResponseBody
    fun getFerdigstiltSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
    ): PapirManuellOppgave {
        log.info("henter ferdigstilt sykmelding med id $sykmeldingId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        val res = smregistreringClient.getFerdigstiltSykmeldingRequest(token, sykmeldingId)
        return res
    }

    @PostMapping("/oppgave/{oppgaveId}/tilgosys")
    fun sendOppgaveTilGosys(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<HttpStatusCode> {
        log.info("Sender oppgave med id $oppgaveId til Gosys gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.postOppgaveTilGosysRequest(token, oppgaveId)
    }
}

// TODO sendPapirSykmeldingManuellOppgave
// TODO endreSykmelding

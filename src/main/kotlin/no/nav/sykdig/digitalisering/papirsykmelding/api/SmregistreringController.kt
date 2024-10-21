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
    ): ResponseEntity<HttpStatusCode> {
        log.info("avviser oppgave med id $oppgaveId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.postAvvisOppgaveRequest(token, oppgaveId, navEnhet, avvisSykmeldingRequest)
    }

    @GetMapping("/oppgave/{oppgaveid}")
    @ResponseBody
    fun getPapirsykmeldingManuellOppgave(
        @PathVariable oppgaveid: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<PapirManuellOppgave> {
        log.info("henter oppgave med id $oppgaveid gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.getOppgaveRequest(token, oppgaveid)
    }

    @GetMapping("/pasient")
    @ResponseBody
    fun getPasientNavn(
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Pasient-Fnr") fnr: String,
    ): ResponseEntity<PasientNavn> {
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.getPasientNavnRequest(token, fnr)
    }

    @GetMapping("/sykmelder/{hprNummer}")
    @ResponseBody
    fun getSykmelder(
        @PathVariable hprNummer: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<Sykmelder> {
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.getSykmelderRequest(token, hprNummer)
    }

    @PostMapping("/oppgave/{oppgaveId}/send")
    fun sendOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.postSendOppgaveRequest(token, oppgaveId, navEnhet, papirSykmelding)
    }

    @GetMapping("/sykmelding/{sykmeldingId}/ferdigstilt")
    @ResponseBody
    fun getFerdigstiltSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<PapirManuellOppgave> {
        log.info("henter ferdigstilt sykmelding med id $sykmeldingId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.getFerdigstiltSykmeldingRequest(token, sykmeldingId)
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

    @PostMapping("/sykmelding/{sykmeldingId}")
    fun korrigerSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.postKorrigerSykmeldingRequest(token, sykmeldingId, navEnhet, papirSykmelding)
    }

    @GetMapping("/pdf/{oppgaveId}/{dokumentInfoId}")
    @ResponseBody
    fun registerPdf(
        @PathVariable oppgaveId: String,
        @PathVariable dokumentInfoId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<ByteArray> {
        log.info("henter pdf med oppgaveId $oppgaveId of dokumentinfoId $dokumentInfoId gjennom syk-dig proxy")
        val token = authorization.removePrefix("Bearer ")
        return smregistreringClient.getRegisterPdfRequest(token, oppgaveId, dokumentInfoId)
    }
}

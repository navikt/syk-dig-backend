package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.securelog
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
import java.util.UUID

@RestController
@RequestMapping("/api/v1/proxy")
class NasjonalOppgaveController(
    private val smregistreringClient: SmregistreringClient,
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val personService: PersonService,
) {
    val log = applog()
    val securelog = securelog()

    @PostMapping("/oppgave/{oppgaveId}/avvis")
    fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody avvisSykmeldingRequest: String,
    ): ResponseEntity<PapirManuellOppgave> {
        log.info("papirsykmelding: henter oppgave med id $oppgaveId gjennom syk-dig proxy")
        val oppgave = smregistreringClient.getOppgaveRequest(authorization, oppgaveId)
        val papirManuellOppgave = oppgave.body
        log.info("papirsykmelding: avviser oppgave med id $oppgaveId")
        val avvisningsgrunn = avvisSykmeldingRequest
        val navEpost = avvisSykmeldingRequest
        if (papirManuellOppgave != null) {
            securelog.info("avviser nasjonalOppgave i db $papirManuellOppgave")
            nasjonalOppgaveService.avvisOppgave(papirManuellOppgave, authorization, navEnhet, navEpost, avvisningsgrunn)
        }
        return oppgave
    }

    @GetMapping("/oppgave/{oppgaveid}")
    @ResponseBody
    fun getPapirsykmeldingManuellOppgave(
        @PathVariable oppgaveid: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<PapirManuellOppgave> {
        log.info("papirsykmelding: henter oppgave med id $oppgaveid gjennom syk-dig proxy")
        val oppgave = smregistreringClient.getOppgaveRequest(authorization, oppgaveid)
        val papirManuellOppgave = oppgave.body
        if (papirManuellOppgave != null) {
            securelog.info("lagrer nasjonalOppgave i db $papirManuellOppgave")
            // nasjonalOppgaveService.lagreOppgave(papirManuellOppgave)
        }
        return oppgave
    }

    @GetMapping("/pasient")
    @ResponseBody
    fun getPasientNavn(
        @RequestHeader("X-Pasient-Fnr") fnr: String,
    ): ResponseEntity<Navn> {
        val callId = UUID.randomUUID().toString()
        log.info("Henter person med callId $callId")

        val personNavn: Navn =
            personService.hentPersonNavn(
                id = fnr,
                callId = callId,
            )
        return ResponseEntity.ok().body(personNavn)
    }

    @GetMapping("/sykmelder/{hprNummer}")
    @ResponseBody
    fun getSykmelder(
        @PathVariable hprNummer: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<Sykmelder> {
        return smregistreringClient.getSykmelderRequest(authorization, hprNummer)
    }

    @PostMapping("/oppgave/{oppgaveId}/send")
    fun sendOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        log.info("papirsykmelding: sender oppgave med oppgaveId $oppgaveId gjennom syk-dig proxy")
        return smregistreringClient.postSendOppgaveRequest(authorization, oppgaveId, navEnhet, papirSykmelding)
    }

    @GetMapping("/sykmelding/{sykmeldingId}/ferdigstilt")
    @ResponseBody
    fun getFerdigstiltSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<PapirManuellOppgave> {
        log.info("papirsykmelding: henter ferdigstilt sykmelding med id $sykmeldingId gjennom syk-dig proxy")
        return smregistreringClient.getFerdigstiltSykmeldingRequest(authorization, sykmeldingId)
    }

    @PostMapping("/oppgave/{oppgaveId}/tilgosys")
    fun sendOppgaveTilGosys(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<HttpStatusCode> {
        log.info("papirsykmelding: Sender oppgave med id $oppgaveId til Gosys gjennom syk-dig proxy")
        return smregistreringClient.postOppgaveTilGosysRequest(authorization, oppgaveId)
    }

    @PostMapping("/sykmelding/{sykmeldingId}")
    fun korrigerSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        log.info("papirsykmelding: Korrrigerer sykmelding med id $sykmeldingId gjennom syk-dig proxy")
        return smregistreringClient.postKorrigerSykmeldingRequest(authorization, sykmeldingId, navEnhet, papirSykmelding)
    }

    @GetMapping("/pdf/{oppgaveId}/{dokumentInfoId}")
    @ResponseBody
    fun registerPdf(
        @PathVariable oppgaveId: String,
        @PathVariable dokumentInfoId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<ByteArray> {
        log.info("papirsykmelding: henter pdf med oppgaveId $oppgaveId of dokumentinfoId $dokumentInfoId gjennom syk-dig proxy")
        return smregistreringClient.getRegisterPdfRequest(authorization, oppgaveId, dokumentInfoId)
    }
}

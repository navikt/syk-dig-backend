package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalSykmeldingService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.securelog
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
    private val sykmelderService: SykmelderService,
    private val personService: PersonService,
    private val nasjonalSykmeldingService: NasjonalSykmeldingService,
) {
    val log = applog()
    val securelog = securelog()

    @PostMapping("/oppgave/{oppgaveId}/avvis")
    fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody avvisSykmeldingRequest: String,
    ): ResponseEntity<HttpStatusCode> {
        log.info("papirsykmelding: avviser oppgave med id $oppgaveId gjennom syk-dig proxy")
        return smregistreringClient.postAvvisOppgaveRequest(authorization, oppgaveId, navEnhet, avvisSykmeldingRequest)
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
            nasjonalOppgaveService.lagreOppgave(papirManuellOppgave)
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
    suspend fun getSykmelder(
        @PathVariable hprNummer: String,
    ): ResponseEntity<Sykmelder> {
        if (hprNummer.isBlank() || !hprNummer.all { it.isDigit() }) {
            log.info("Ugyldig path parameter: hprNummer")
            return ResponseEntity.badRequest().build()
        }
        val callId = UUID.randomUUID().toString()
        securelog.info("Henter person med callId $callId and hprNummer = $hprNummer")
        val sykmelder = sykmelderService.getSykmelder(hprNummer, callId)
        return ResponseEntity.ok(sykmelder)
    }

    @PostMapping("/oppgave/{oppgaveId}/send")
    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @ResponseBody
    suspend fun sendOppgave(
        @PathVariable oppgaveId: Int,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<Any> {
        val callId = UUID.randomUUID().toString()
        return nasjonalSykmeldingService.sendPapirsykmelding(papirSykmelding, navEnhet, callId, oppgaveId)

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

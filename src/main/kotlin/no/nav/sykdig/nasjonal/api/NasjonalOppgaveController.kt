package no.nav.sykdig.nasjonal.api

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.sykdig.shared.applog
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.nasjonal.services.NasjonalSykmeldingService
import no.nav.sykdig.nasjonal.models.PapirManuellOppgave
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.pdl.Navn
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.shared.securelog
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/proxy")
class NasjonalOppgaveController(
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val sykmelderService: SykmelderService,
    private val personService: PersonService,
    private val nasjonalSykmeldingService: NasjonalSykmeldingService,
) {
    val log = applog()
    val securelog = securelog()

    @PostMapping("/oppgave/{oppgaveId}/avvis")
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization, '/oppgave/{oppgaveId}/avvis')")
    @WithSpan
    suspend fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody avvisSykmeldingRequest: String,
    ): ResponseEntity<HttpStatusCode> {
        log.info("Forsøker å avvise oppgave med oppgaveId: $oppgaveId")
        return nasjonalOppgaveService.avvisOppgave(oppgaveId, avvisSykmeldingRequest, navEnhet, authorization)
    }

    @GetMapping("/oppgave/{oppgaveId}")
    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization, '/oppgave/{oppgaveId}')")
    @ResponseBody
    @WithSpan
    fun getPapirsykmeldingManuellOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<Any> {
        val papirManuellOppgave = nasjonalOppgaveService.getOppgave(oppgaveId, authorization)

        if (papirManuellOppgave != null) {
            if(papirManuellOppgave.ferdigstilt) {
                log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Oppgave med id $oppgaveId er allerede ferdigstilt")
            }
            return ResponseEntity.ok(nasjonalOppgaveService.mapFromDao(papirManuellOppgave))
        }

        return ResponseEntity.notFound().build()
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
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization, '/oppgave/{oppgaveId}/send')")
    @ResponseBody
    @WithSpan
    suspend fun sendOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<Any> {
        val callId = UUID.randomUUID().toString()
        return nasjonalSykmeldingService.sendPapirsykmeldingOppgave(papirSykmelding, navEnhet, callId, oppgaveId, authorization)

    }

    @GetMapping("/sykmelding/{sykmeldingId}/ferdigstilt")
    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalSykmelding(#sykmeldingId, #authorization, '/sykmelding/{sykmeldingId}/ferdigstilt')")
    @ResponseBody
    @WithSpan
    fun getFerdigstiltSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<PapirManuellOppgave> {
        val oppgave = nasjonalOppgaveService.getOppgaveBySykmeldingId(sykmeldingId, authorization)
        if (oppgave != null) {
            if(!oppgave.ferdigstilt) {
                log.info("Oppgave med id $sykmeldingId er ikke ferdigstilt")
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }
            return ResponseEntity.ok(nasjonalOppgaveService.mapFromDao(oppgave))
        }
        return ResponseEntity.notFound().build()
    }

    @PostMapping("/oppgave/{oppgaveId}/tilgosys")
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization, '/oppgave/{oppgaveId}/tilgosys')")
    @WithSpan
    suspend fun sendOppgaveTilGosys(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<HttpStatusCode> {
        if (oppgaveId.isBlank()) {
            log.info("oppgaveId mangler for å kunne sende oppgave til Gosys")
            return ResponseEntity.badRequest().build()
        }
        log.info("papirsykmelding: Sender oppgave med id $oppgaveId til Gosys")
        nasjonalOppgaveService.oppgaveTilGosys(oppgaveId, authorization)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/sykmelding/{sykmeldingId}")
    @PreAuthorize("@oppgaveSecurityService.hasSuperUserAccessToNasjonalSykmelding(#sykmeldingId, #authorization, '/sykmelding/{sykmeldingId}')")
    @WithSpan
    suspend fun korrigerSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<Any> {
        securelog.info("Oppdaterer korrigert oppgave i syk-dig-backend db $papirSykmelding")
        return nasjonalSykmeldingService.korrigerSykmelding(sykmeldingId, navEnhet, UUID.randomUUID().toString(), papirSykmelding, authorization)
    }

    @GetMapping("/pdf/{oppgaveId}/{dokumentInfoId}")
    @ResponseBody
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization, '/pdf/{oppgaveId}/{dokumentInfoId}')")
    @WithSpan
    fun registerPdf(
        @PathVariable oppgaveId: String,
        @PathVariable dokumentInfoId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<Any> {
        log.info("Forsøker å hente pdf for oppgaveId $oppgaveId og dokumentInfoId $dokumentInfoId")
        return nasjonalOppgaveService.getRegisterPdf(oppgaveId, authorization, dokumentInfoId)
    }
}

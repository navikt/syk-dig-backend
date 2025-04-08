package no.nav.sykdig.nasjonal.api

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.sykdig.shared.applog
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.securelog
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/proxy")
class NasjonalOppgaveController(
    private val nasjonalOppgaveService: NasjonalOppgaveService,
) {
    val log = applog()
    val securelog = securelog()

    @PostMapping("/oppgave/{oppgaveId}/avvis")
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/oppgave/{oppgaveId}/avvis')")
    @WithSpan
    suspend fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody avvisSykmeldingRequest: String,
    ): ResponseEntity<HttpStatusCode> {
        log.info("Forsøker å avvise oppgave med oppgaveId: $oppgaveId")
        return nasjonalOppgaveService.avvisOppgave(oppgaveId, avvisSykmeldingRequest, navEnhet)
    }

    @GetMapping("/pdf/{oppgaveId}/{dokumentInfoId}")
    @ResponseBody
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/pdf/{oppgaveId}/{dokumentInfoId}')")
    @WithSpan
    fun registerPdf(
        @PathVariable oppgaveId: String,
        @PathVariable dokumentInfoId: String,
    ): ResponseEntity<Any> {
        log.info("Forsøker å hente pdf for oppgaveId $oppgaveId og dokumentInfoId $dokumentInfoId")
        return nasjonalOppgaveService.getRegisterPdf(oppgaveId, dokumentInfoId)
    }
}

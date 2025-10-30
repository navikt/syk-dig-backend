package no.nav.sykdig.nasjonal.api

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.securelog
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
class NasjonalDocumentController(private val nasjonalOppgaveService: NasjonalOppgaveService) {
    val log = applog()
    val securelog = securelog()

    @GetMapping("/api/document/nasjonal/{oppgaveId}/{dokumentInfoId}")
    @ResponseBody
    @PreAuthorize(
        "@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/document/nasjonal/{oppgaveId}/{dokumentInfoId}')"
    )
    @WithSpan
    fun registerPdf(
        @PathVariable oppgaveId: String,
        @PathVariable dokumentInfoId: String,
    ): ResponseEntity<Any> {
        log.info(
            "Forsøker å hente nasjonal pdf for oppgaveId $oppgaveId og dokumentInfoId $dokumentInfoId"
        )
        return nasjonalOppgaveService.getRegisterPdf(oppgaveId, dokumentInfoId)
    }
}

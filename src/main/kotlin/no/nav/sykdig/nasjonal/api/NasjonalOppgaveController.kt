package no.nav.sykdig.nasjonal.api

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.sykdig.digitalisering.papirsykmelding.mapFromDao
import no.nav.sykdig.shared.applog
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.nasjonal.services.NasjonalDbService
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
    private val nasjonalDbService: NasjonalDbService
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

    @GetMapping("/oppgave/{oppgaveId}")
    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/oppgave/{oppgaveId}')")
    @ResponseBody
    @WithSpan
    fun getPapirsykmeldingManuellOppgave(
        @PathVariable oppgaveId: String,
    ): ResponseEntity<Any> {
        val papirManuellOppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId)
        if (papirManuellOppgave != null) {
            if(papirManuellOppgave.ferdigstilt) {
                log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Oppgave med id $oppgaveId er allerede ferdigstilt")
            }
            return ResponseEntity.ok(mapFromDao(papirManuellOppgave))
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
            personService.getPersonNavn(
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
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/oppgave/{oppgaveId}/send')")
    @ResponseBody
    @WithSpan
    suspend fun sendOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<Any> {
        val callId = UUID.randomUUID().toString()
        return nasjonalOppgaveService.sendNasjonalOppgave(papirSykmelding, navEnhet, callId, oppgaveId)
    }

    @PostMapping("/oppgave/{oppgaveId}/tilgosys")
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/oppgave/{oppgaveId}/tilgosys')")
    @WithSpan
    fun sendOppgaveTilGosys(
        @PathVariable oppgaveId: String,
    ): ResponseEntity<HttpStatusCode> {
        if (oppgaveId.isBlank()) {
            log.info("oppgaveId mangler for å kunne sende oppgave til Gosys")
            return ResponseEntity.badRequest().build()
        }
        log.info("papirsykmelding: Sender oppgave med id $oppgaveId til Gosys")
        nasjonalOppgaveService.oppgaveTilGosys(oppgaveId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/sykmelding/{sykmeldingId}")
    @PreAuthorize("@oppgaveSecurityService.hasSuperUserAccessToNasjonalSykmelding(#sykmeldingId, null, '/sykmelding/{sykmeldingId}')")
    @WithSpan
    suspend fun korrigerSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<Any> {
        securelog.info("Oppdaterer korrigert oppgave i syk-dig-backend db $papirSykmelding")
        return nasjonalOppgaveService.korrigerSykmelding(sykmeldingId, navEnhet, UUID.randomUUID().toString(), papirSykmelding)
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

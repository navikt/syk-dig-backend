package no.nav.sykdig.digitalisering.papirsykmelding.api

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalCommonService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalSykmeldingService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.Utfall
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.securelog
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/api/v1/proxy")
class NasjonalOppgaveController(
    private val smregistreringClient: SmregistreringClient,
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val sykmelderService: SykmelderService,
    private val personService: PersonService,
    private val nasjonalSykmeldingService: NasjonalSykmeldingService,
    private val nasjonalCommonService: NasjonalCommonService,
) {
    val log = applog()
    val securelog = securelog()

    @PostMapping("/oppgave/{oppgaveId}/avvis")
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization)")
    fun avvisOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestBody avvisSykmeldingRequest: String,
    ): ResponseEntity<HttpStatusCode> {
        log.info("Forsøker å avvise oppgave med oppgaveId: $oppgaveId")
        return nasjonalOppgaveService.avvisOppgave(oppgaveId, avvisSykmeldingRequest, navEnhet, authorization)
    }

    @GetMapping("/oppgave/{oppgaveId}")
    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization)")
    @ResponseBody
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
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization)")
    @ResponseBody
    suspend fun sendOppgave(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<Any> {
        val callId = UUID.randomUUID().toString()
        return nasjonalSykmeldingService.sendPapirsykmelding(papirSykmelding, navEnhet, callId, oppgaveId, authorization)

    }

    @GetMapping("/sykmelding/{sykmeldingId}/ferdigstilt")
    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalSykmelding(#sykmeldingId, #authorization)")
    @ResponseBody
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
    fun sendOppgaveTilGosys(
        @PathVariable oppgaveId: String,
        @RequestHeader("Authorization") authorization: String,
    ): ResponseEntity<HttpStatusCode> {
        log.info("papirsykmelding: Sender oppgave med id $oppgaveId til Gosys gjennom syk-dig proxy")
        return smregistreringClient.postOppgaveTilGosysRequest(authorization, oppgaveId)
    }

    @PostMapping("/sykmelding/{sykmeldingId}")
    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalSykmelding(#sykmeldingId, #authorization)")
    fun korrigerSykmelding(
        @PathVariable sykmeldingId: String,
        @RequestHeader("Authorization") authorization: String,
        @RequestHeader("X-Nav-Enhet") navEnhet: String,
        @RequestBody papirSykmelding: SmRegistreringManuell,
    ): ResponseEntity<String> {
        log.info("papirsykmelding: Korrrigerer sykmelding med id $sykmeldingId gjennom syk-dig proxy")
        val res = smregistreringClient.postKorrigerSykmeldingRequest(authorization, sykmeldingId, navEnhet, papirSykmelding)

        securelog.info("Oppdaterer korrigert oppgave i syk-dig-backend db $papirSykmelding")
        nasjonalOppgaveService.oppdaterOppgave(
            sykmeldingId = sykmeldingId,
            utfall = Utfall.OK.toString(),
            ferdigstiltAv = nasjonalCommonService.getNavIdent().veilederIdent,
            avvisningsgrunn = null,
            smRegistreringManuell = papirSykmelding,
        )
        return res
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

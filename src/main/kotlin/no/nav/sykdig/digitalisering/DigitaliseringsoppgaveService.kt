package no.nav.sykdig.digitalisering

import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.digitalisering.ferdigstilling.SendTilGosysService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.regelvalidering.RegelvalideringService
import no.nav.sykdig.logger
import no.nav.sykdig.metrics.MetricRegister
import org.springframework.stereotype.Service

@Service
class DigitaliseringsoppgaveService(
    private val oppgaveService: OppgaveService,
    private val sendTilGosysService: SendTilGosysService,
    private val personService: PersonService,
    private val metricRegister: MetricRegister,
    private val regelvalideringService: RegelvalideringService,
) {

    private val log = logger()

    fun getDigitaiseringsoppgave(oppgaveId: String): SykDigOppgave {
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )

        log.info("Hentet oppgave og sykmeldt for oppgave $oppgaveId, lager SykDigOppgave!")

        return SykDigOppgave(oppgave, sykmeldt)
    }

    fun updateOppgave(oppgaveId: String, values: RegisterOppgaveValues, navEpost: String) {
        oppgaveService.updateOppgave(oppgaveId, values, navEpost)
    }

    fun ferdigstillOppgave(
        oppgaveId: String,
        navEpost: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String,
    ) {
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(sykmeldt, values)
        if (valideringsresultat.isNotEmpty()) {
            log.warn("Ferdigstilling av oppgave med id $oppgaveId feilet pga regelsjekk")
            throw ClientException(valideringsresultat.joinToString())
        }
        oppgaveService.ferdigstillOppgave(oppgave, navEpost, values, enhetId, sykmeldt)
        metricRegister.FERDIGSTILT_OPPGAVE.increment()
    }

    fun ferdigstillOppgaveSendTilGosys(
        oppgaveId: String,
        navIdent: String,
        navEpost: String,
    ): SykDigOppgave {
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )

        sendTilGosysService.sendOppgaveTilGosys(oppgaveId, oppgave.sykmeldingId.toString(), navIdent)
        oppgaveService.ferdigstillOppgaveGosys(oppgave, navEpost)
        val updatedOppgave = oppgaveService.getOppgave(oppgaveId)

        metricRegister.SENDT_TIL_GOSYS.increment()
        return SykDigOppgave(updatedOppgave, sykmeldt)
    }
}

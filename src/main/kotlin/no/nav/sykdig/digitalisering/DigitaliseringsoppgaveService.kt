package no.nav.sykdig.digitalisering

import no.nav.sykdig.digitalisering.ferdigstilling.SendTilGosysService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.metrics.MetricRegister
import org.springframework.stereotype.Service

@Service
class DigitaliseringsoppgaveService(
    private val oppgaveService: OppgaveService,
    private val sendTilGosysService: SendTilGosysService,
    private val personService: PersonService,
    private val metricRegister: MetricRegister
) {

    fun getDigitaiseringsoppgave(oppgaveId: String): SykDigOppgave {
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString()
        )
        return SykDigOppgave(oppgave, sykmeldt)
    }

    fun updateOppgave(oppgaveId: String, values: RegisterOppgaveValues, ident: String) {
        oppgaveService.updateOppgave(oppgaveId, values, ident)
    }

    fun ferdigstillOppgave(
        oppgaveId: String,
        ident: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String
    ) {
        oppgaveService.ferdigstillOppgave(oppgaveId, ident, values, enhetId)
        metricRegister.FERDIGSTILT_OPPGAVE.increment()
    }

    fun ferdigstillOppgaveSendTilGosys(
        oppgaveId: String,
        ident: String,
    ): SykDigOppgave {
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString()
        )

        sendTilGosysService.sendOppgaveTilGosys(oppgaveId, oppgave.sykmeldingId.toString(), ident)
        oppgaveService.ferdigstillOppgaveGosys(oppgave.oppgaveId, ident)
        val updatedOppgave = oppgaveService.getOppgave(oppgaveId)

        metricRegister.SENDT_TIL_GOSYS.increment()
        return SykDigOppgave(updatedOppgave, sykmeldt)
    }
}

package no.nav.sykdig.digitalisering

import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.SendTilGosysService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.PersonService
import org.springframework.stereotype.Service

@Service
class DigitaliseringsoppgaveService(
    private val oppgaveService: OppgaveService,
    private val ferdigstillingService: FerdigstillingService,
    private val sendTilGosysService: SendTilGosysService,
    private val personService: PersonService
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
    }

    fun ferdigstillOppgaveSendTilGosys(
        oppgaveId: String,
        ident: String,
    ) {
        val sykmeldingId = oppgaveService.getOppgave(oppgaveId).sykmeldingId.toString()
        sendTilGosysService.sendOppgaveTilGosys(oppgaveId, sykmeldingId, ident)
        oppgaveService.ferdigstillOppgaveGosys(oppgaveId)
    }
}

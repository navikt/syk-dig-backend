package no.nav.sykdig.utenlandsk.services

import no.nav.sykdig.oppgave.OppgaveClient
import no.nav.sykdig.oppgave.model.OppgaveStatus
import org.springframework.stereotype.Component

@Component
class GosysService(
    private val oppgaveClient: OppgaveClient,
    ) {
        fun sendOppgaveTilGosys(
            oppgaveId: String,
            sykmeldingId: String,
            veilederNavIdent: String,
            beskrivelse: String? = null,
        ) {
            val oppgave = oppgaveClient.getOppgave(oppgaveId, sykmeldingId)

            oppgaveClient.oppdaterGosysOppgave(
                oppgaveId,
                sykmeldingId,
                oppgave.versjon,
                oppgave.status,
                "FS22",
                veilederNavIdent,
                beskrivelse,
            )
        }
    // TODO: flytt denne til nasjonal
    fun sendNasjonalOppgaveTilGosys(
        oppgaveId: String,
        sykmeldingId: String,
        veilederNavIdent: String,
        beskrivelse: String? = null,
    ) {
        val oppgave = oppgaveClient.getNasjonalOppgave(oppgaveId, sykmeldingId)
        val oppdatertOppgave = oppgave.copy(
            behandlesAvApplikasjon = "FS22",
            tilordnetRessurs = veilederNavIdent
        )
        oppgaveClient.oppdaterNasjonalGosysOppgave(
            oppdatertOppgave,
            sykmeldingId,
            oppgaveId,
            veilederNavIdent
        )
    }


    fun avvisOppgaveTilGosys(
        oppgaveId: String,
        sykmeldingId: String,
        veilederNavIdent: String,
        beskrivelse: String? = null,
    ) {
        val oppgave = oppgaveClient.getOppgave(oppgaveId, sykmeldingId)

        oppgaveClient.oppdaterGosysOppgave(
            oppgaveId,
            sykmeldingId,
            oppgave.versjon,
            OppgaveStatus.FERDIGSTILT,
            "FS22",
            veilederNavIdent,
            beskrivelse,
        )
    }

    fun hentOppgave(
        oppgaveId: String,
        sykmeldingId: String,
    ) = oppgaveClient.getOppgave(oppgaveId, sykmeldingId)
}

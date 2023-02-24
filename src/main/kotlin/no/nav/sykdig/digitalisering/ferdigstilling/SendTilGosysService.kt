package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import org.springframework.stereotype.Component

@Component
class SendTilGosysService(
    private val oppgaveClient: OppgaveClient,
) {
    fun sendOppgaveTilGosys(
        oppgaveId: String,
        sykmeldingId: String,
        veilederIdent: String,
    ) {
        val oppgave = oppgaveClient.getOppgave(oppgaveId, sykmeldingId)

        oppgaveClient.oppdaterOppgave(
            oppgaveId,
            sykmeldingId,
            oppgave.versjon,
            oppgave.status,
            "FS22",
            veilederIdent,
        )
    }
}

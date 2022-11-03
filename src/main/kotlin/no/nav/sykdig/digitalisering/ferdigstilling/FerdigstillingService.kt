package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.logger
import org.springframework.stereotype.Component

@Component
class FerdigstillingService(
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient,
    private val dokarkivClient: DokarkivClient,
    private val oppgaveClient: OppgaveClient
) {
    val log = logger()

    fun ferdigstill(
        oppgaveId: String,
        navnSykmelder: String?,
        land: String,
        fnr: String,
        enhet: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String
    ) {
        if (safJournalpostGraphQlClient.erFerdigstilt(journalpostId)) {
            log.info("Journalpost med id $journalpostId er allerede ferdigstilt, sykmeldingId $sykmeldingId")
        } else {
            dokarkivClient.oppdaterOgFerdigstillJournalpost(
                navnSykmelder = navnSykmelder,
                land = land,
                fnr = fnr,
                enhet = enhet,
                dokumentinfoId = dokumentinfoId,
                journalpostId = journalpostId,
                sykmeldingId = sykmeldingId
            )
        }
        oppgaveClient.ferdigstillOppgave(oppgaveId = oppgaveId, sykmeldingId = sykmeldingId)
        // skriv til topic
    }
}

package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.logger
import org.springframework.stereotype.Component

@Component
class FerdigstillingService(
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient,
    private val dokarkivClient: DokarkivClient
) {
    val log = logger()

    fun ferdigstill(
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
        // ferdigstill oppgave
        // ferdigstill i database
    }
}

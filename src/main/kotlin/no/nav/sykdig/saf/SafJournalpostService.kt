package no.nav.sykdig.saf

import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import no.nav.sykdig.shared.applog
import no.nav.sykdig.saf.graphql.DokumentInfo
import no.nav.sykdig.saf.graphql.Journalstatus
import org.springframework.stereotype.Component

@Component
class SafJournalpostService(
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient,
) {
    val logger = applog()

    fun getDokumenterM2m(
        journalpostId: String,
        sykmeldingId: String,
        source: String,
    ): List<DokumentMedTittel>? {
        val journalpost =
            safJournalpostGraphQlClient.getJournalpostM2m(
                journalpostId = journalpostId,
            )

        journalpost.journalpost?.let {
            if (it.kanal != "EESSI" && source == "rina") {
                logger.warn(
                    "Journalpost med id $journalpostId har ikke forventet mottakskanal: ${it.kanal}, $sykmeldingId",
                )
            }
            if (it.dokumenter?.any { it.brevkode == "S055" } == false && source == "rina") {
                logger.warn(
                    "Journalpost med id $journalpostId har ingen dokumenter med forventet brevkode, $sykmeldingId",
                )
            }

            if (erIkkeJournalfort(it.journalstatus)) {
                return finnDokumentInfoIdForSykmeldingPdfListe(it.dokumenter, sykmeldingId)
            } else {
                logger.warn(
                    "Journalpost med id $journalpostId er allerede journalført, sporingsId $sykmeldingId",
                )
                return null
            }
        }
        logger.warn("Fant ikke journalpost med id $journalpostId, $sykmeldingId")
        return null
    }

    private fun erIkkeJournalfort(journalpostStatus: Journalstatus?): Boolean {
        return journalpostStatus?.let {
            it == Journalstatus.MOTTATT || it == Journalstatus.FEILREGISTRERT
        }
            ?: false
    }

    fun erIkkeJournalfort(journalpostId: String): Boolean {
        val journalpost = safJournalpostGraphQlClient.getJournalpostNasjonal(journalpostId)
        return erIkkeJournalfort(journalpost.journalpost?.journalstatus)
    }

    private fun finnDokumentInfoIdForSykmeldingPdfListe(
        dokumentListe: List<DokumentInfo>?,
        sykmeldingId: String,
    ): List<DokumentMedTittel> {
        val dokumenter =
            dokumentListe
                ?.filter { dokument ->
                    dokument.dokumentvarianter?.any { it.variantformat == "ARKIV" } == true
                }?.mapNotNull {
                    if (it.tittel != null) {
                        DokumentMedTittel(it.dokumentInfoId, it.tittel)
                    } else {
                        null
                    }
                }

        if (dokumenter.isNullOrEmpty()) {
            logger.error("Fant ikke PDF-dokument for sykmelding, $sykmeldingId")
            throw RuntimeException("Journalpost mangler PDF, $sykmeldingId")
        }

        return dokumenter
    }
}

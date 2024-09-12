package no.nav.sykdig.digitalisering.saf

import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.saf.graphql.DokumentInfo
import no.nav.sykdig.digitalisering.saf.graphql.Journalstatus
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import org.springframework.stereotype.Component

@Component
class SafJournalpostService(
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient,
) {
    val logger = applog()

    fun getDokumenter(
        journalpostId: String,
        sykmeldingId: String,
        source: String,
    ): List<DokumentMedTittel>? {
        val journalpost =
            safJournalpostGraphQlClient.getJournalpost(
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

            if (erIkkeJournalfort(it)) {
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

    private fun erIkkeJournalfort(journalpostResponse: SafJournalpost): Boolean {
        return journalpostResponse.journalstatus?.let {
            it == Journalstatus.MOTTATT || it == Journalstatus.FEILREGISTRERT
        }
            ?: false
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

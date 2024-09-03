package no.nav.oppgavelytter.oppgave.saf

import no.nav.oppgavelytter.accesstoken.AccessTokenClient
import no.nav.oppgavelytter.oppgave.saf.client.SafGraphQlClient
import no.nav.oppgavelytter.oppgave.saf.client.model.DokumentInfo
import no.nav.oppgavelytter.oppgave.saf.client.model.JournalpostResponse
import no.nav.oppgavelytter.oppgave.saf.model.DokumentMedTittel
import no.nav.sykdig.applog

class SafJournalpostService(
    private val safGraphQlClient: SafGraphQlClient,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String,
) {
    val logger = applog()

    fun getDokumenter(
        journalpostId: String,
        sporingsId: String,
        source: String,
    ): List<DokumentMedTittel>? {
        val journalpost =
            safGraphQlClient.findJournalpost(
                journalpostId = journalpostId,
                token = accessTokenClient.getAccessToken(scope).toString(),
                sporingsId = sporingsId,
            )
        journalpost!!.errors?.forEach {
            logger.error(
                "Feil ved henting av journalpost med id $journalpostId fra SAF: ${it.message}",
            )
        }
        if (journalpost.data == null) {
            logger.error(
                "Mottatt journalføringsoppgave for journalpost som ikke finnes: journalpostId: $journalpostId, $sporingsId",
            )
            throw RuntimeException("Mottatt journalføringsoppgave for journalpost som ikke finnes")
        }

        journalpost.data.journalpost?.let {
            if (it.kanal != "EESSI" && source == "rina") {
                logger.warn(
                    "Journalpost med id $journalpostId har ikke forventet mottakskanal: ${it.kanal}, $sporingsId",
                )
            }
            if (it.dokumenter?.any { it.brevkode == "S055" } == false && source == "rina") {
                logger.warn(
                    "Journalpost med id $journalpostId har ingen dokumenter med forventet brevkode, $sporingsId",
                )
            }

            if (erIkkeJournalfort(it)) {
                return finnDokumentInfoIdForSykmeldingPdfListe(it.dokumenter, sporingsId)
            } else {
                logger.warn(
                    "Journalpost med id $journalpostId er allerede journalført, sporingsId $sporingsId",
                )
                return null
            }
        }
        logger.warn("Fant ikke journalpost med id $journalpostId, $sporingsId")
        return null
    }

    private fun erIkkeJournalfort(journalpostResponse: JournalpostResponse): Boolean {
        return journalpostResponse.journalstatus?.let {
            it.equals("MOTTATT", true) || it.equals("FEILREGISTRERT", true)
        }
            ?: false
    }

    private fun finnDokumentInfoIdForSykmeldingPdfListe(
        dokumentListe: List<DokumentInfo>?,
        sporingsId: String,
    ): List<DokumentMedTittel> {
        val dokumenter =
            dokumentListe
                ?.filter { dokument ->
                    dokument.dokumentvarianter?.any { it.variantformat == "ARKIV" } == true
                }
                ?.map { DokumentMedTittel(it.dokumentInfoId, it.tittel) }

        if (dokumenter.isNullOrEmpty()) {
            logger.error("Fant ikke PDF-dokument for sykmelding, $sporingsId")
            throw RuntimeException("Journalpost mangler PDF, $sporingsId")
        }

        return dokumenter
    }
}

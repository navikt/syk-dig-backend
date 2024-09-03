package no.nav.oppgavelytter.oppgave

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.oppgavelytter.oppgave.client.OppdaterOppgaveRequest
import no.nav.oppgavelytter.oppgave.client.OppgaveClient
import no.nav.oppgavelytter.oppgave.client.OppgaveResponse
import no.nav.oppgavelytter.oppgave.saf.SafJournalpostService
import no.nav.oppgavelytter.oppgave.sykdig.DigitaliseringsoppgaveKafka
import no.nav.oppgavelytter.oppgave.sykdig.SykDigProducer
import no.nav.sykdig.applog
import java.util.UUID

const val NAV_OPPFOLGNING_UTLAND = "0393"

class OppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val safJournalpostService: SafJournalpostService,
    private val sykDigProducer: SykDigProducer,
    private val cluster: String,
) {
    @WithSpan
    fun handleOppgave(
        @SpanAttribute oppgaveId: Long,
        fnr: String,
    ) {
        val sporingsId = UUID.randomUUID().toString()
        val logger = applog()
        val oppgave = oppgaveClient.hentOppgave(oppgaveId = oppgaveId, sporingsId = sporingsId)

        if (
            (
                oppgave.gjelderUtenlandskSykmeldingFraRina() ||
                    oppgave.gjelderUtenlandskSykmeldingFraNAVNO()
            ) &&
            !oppgave.journalpostId.isNullOrEmpty()
        ) {
            logger.info(
                "Oppgave med id $oppgaveId og journalpostId ${oppgave.journalpostId} gjelder utenlandsk sykmelding, " +
                    "sporingsId $sporingsId",
            )

            logger.info(
                "Utenlandsk sykmelding: OppgaveId $oppgaveId, journalpostId ${oppgave.journalpostId}",
            )
            if (oppgave.erTildeltNavOppfolgningUtlang() || cluster == "dev-gcp") {
                val dokumenter =
                    safJournalpostService.getDokumenter(
                        journalpostId = oppgave.journalpostId,
                        sporingsId = sporingsId,
                        source = setSoruce(oppgave),
                    )
                if (dokumenter != null) {
                    oppgaveClient.oppdaterOppgave(
                        OppdaterOppgaveRequest(
                            id = oppgaveId.toInt(),
                            behandlesAvApplikasjon = "SMD",
                            versjon = oppgave.versjon,
                        ),
                        sporingsId,
                    )
                    sykDigProducer.send(
                        sporingsId,
                        DigitaliseringsoppgaveKafka(
                            oppgaveId = oppgaveId.toString(),
                            fnr = fnr,
                            journalpostId = oppgave.journalpostId,
                            dokumentInfoId = dokumenter.first().dokumentInfoId,
                            type = "UTLAND",
                            dokumenter = dokumenter,
                            source = setSoruce(oppgave),
                        ),
                    )
                    logger.info(
                        "Sendt sykmelding til syk-dig for oppgaveId $oppgaveId, " +
                            "sporingsId $sporingsId, journalpostId " +
                            "${oppgave.journalpostId}",
                    )
                } else {
                    logger.warn("Oppgaven $oppgaveId har ikke dokumenter, hopper over")
                }
            } else {
                logger.warn(
                    "Oppgaven $oppgaveId, journalpostId ${oppgave.journalpostId} er ikke tildelt " +
                        NAV_OPPFOLGNING_UTLAND,
                )
            }
        }
    }

    private fun OppgaveResponse.gjelderUtenlandskSykmeldingFraRina(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() &&
            behandlesAvApplikasjon == null &&
            tema == "SYM" &&
            behandlingstype == "ae0106" &&
            behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR" &&
            metadata?.get("RINA_SAKID") != null
    }

    private fun OppgaveResponse.gjelderUtenlandskSykmeldingFraNAVNO(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() &&
            behandlesAvApplikasjon == null &&
            tema == "SYK" &&
            behandlingstype == "ae0106" &&
            behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR"
    }

    private fun setSoruce(oppgave: OppgaveResponse): String {
        return if (oppgave.gjelderUtenlandskSykmeldingFraRina()) {
            "rina"
        } else if (oppgave.gjelderUtenlandskSykmeldingFraNAVNO()) {
            "navno"
        } else {
            throw RuntimeException("Ukjent type kilde")
        }
    }

    private fun OppgaveResponse.erTildeltNavOppfolgningUtlang() = tildeltEnhetsnr == NAV_OPPFOLGNING_UTLAND
}

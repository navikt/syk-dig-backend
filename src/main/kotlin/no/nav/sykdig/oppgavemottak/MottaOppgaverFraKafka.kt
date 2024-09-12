package no.nav.sykdig.oppgavemottak

import no.nav.oppgavelytter.oppgave.NAV_OPPFOLGNING_UTLAND
import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import no.nav.sykdig.applog
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.GetOppgaveResponse
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppdaterOppgaveRequest
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.saf.SafJournalpostService
import no.nav.sykdig.metrics.MetricRegister
import no.nav.sykdig.utils.toOppgaveDbModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Component
class MottaOppgaverFraKafka(
    private val oppgaveRepository: OppgaveRepository,
    private val metricRegister: MetricRegister,
    private val oppgaveClient: OppgaveClient,
    @Value("\${nais.cluster}") private val cluster: String,
    private val safJournalpostService: SafJournalpostService,
) {
    val logger = applog()

    fun behandleOppgave(oppgaveKafkaAivenRecord: OppgaveKafkaAivenRecord) {
        val sykmeldingId = UUID.randomUUID().toString()
        val oppgaveId = oppgaveKafkaAivenRecord.oppgave.oppgaveId
        val oppgave =
            oppgaveClient.getOppgave(
                oppgaveId = oppgaveKafkaAivenRecord.oppgave.oppgaveId.toString(),
                sykmeldingId = sykmeldingId,
            )

        if (
            (
                oppgave.gjelderUtenlandskSykmeldingFraRina() ||
                    oppgave.gjelderUtenlandskSykmeldingFraNAVNO()
            ) &&
            !oppgave.journalpostId.isNullOrEmpty()
        ) {
            logger.info(
                "Oppgave med id $oppgaveId og journalpostId ${oppgave.journalpostId} gjelder utenlandsk sykmelding, sykmeldingId $sykmeldingId",
            )

            logger.info(
                "Utenlandsk sykmelding: OppgaveId $oppgaveId, journalpostId ${oppgave.journalpostId}",
            )
            if (oppgave.erTildeltNavOppfolgningUtlang() || cluster == "dev-gcp") {
                val dokumenter =
                    safJournalpostService.getDokumenter(
                        journalpostId = oppgave.journalpostId,
                        sykmeldingId = sykmeldingId,
                        source = setSoruce(oppgave),
                    )
                if (dokumenter != null) {
                    oppgaveClient.oppdaterOppgave(
                        OppdaterOppgaveRequest(
                            id = oppgaveId.toInt(),
                            behandlesAvApplikasjon = "SMD",
                            versjon = oppgave.versjon,
                        ),
                        sykmeldingId,
                    )
                    val digitaliseringsoppgave =
                        DigitaliseringsoppgaveScanning(
                            oppgaveId = oppgaveId.toString(),
                            fnr = oppgaveKafkaAivenRecord.oppgave.bruker!!.ident,
                            journalpostId = oppgave.journalpostId,
                            dokumentInfoId = dokumenter.first().dokumentInfoId,
                            type = "UTLAND",
                            dokumenter = dokumenter,
                            source = setSoruce(oppgave),
                        )

                    logger.info("Mottatt oppgave med id ${digitaliseringsoppgave.oppgaveId} for sykmeldingId $sykmeldingId")
                    val opprettet = OffsetDateTime.now(ZoneOffset.UTC)
                    oppgaveRepository.lagreOppgave(
                        toOppgaveDbModel(digitaliseringsoppgave, opprettet, sykmeldingId),
                    )
                    metricRegister.mottatOppgave.increment()
                } else {
                    logger.warn("Oppgaven $oppgaveId har ikke dokumenter, hopper over")
                }
            } else {
                logger.warn(
                    "Oppgaven $oppgaveId, journalpostId ${oppgave.journalpostId} er ikke tildelt $NAV_OPPFOLGNING_UTLAND",
                )
            }
        }
    }

    private fun GetOppgaveResponse.gjelderUtenlandskSykmeldingFraRina(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() &&
            behandlesAvApplikasjon == null &&
            tema == "SYM" &&
            behandlingstype == "ae0106" &&
            behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR" &&
            metadata?.get("RINA_SAKID") != null
    }

    private fun GetOppgaveResponse.gjelderUtenlandskSykmeldingFraNAVNO(): Boolean {
        return ferdigstiltTidspunkt.isNullOrEmpty() &&
            behandlesAvApplikasjon == null &&
            tema == "SYK" &&
            behandlingstype == "ae0106" &&
            behandlingstema.isNullOrEmpty() &&
            oppgavetype == "JFR"
    }

    private fun setSoruce(oppgave: GetOppgaveResponse): String {
        return if (oppgave.gjelderUtenlandskSykmeldingFraRina()) {
            "rina"
        } else if (oppgave.gjelderUtenlandskSykmeldingFraNAVNO()) {
            "navno"
        } else {
            throw RuntimeException("Ukjent type kilde")
        }
    }

    private fun GetOppgaveResponse.erTildeltNavOppfolgningUtlang() = tildeltEnhetsnr == NAV_OPPFOLGNING_UTLAND
}

data class DigitaliseringsoppgaveScanning(
    val oppgaveId: String,
    val fnr: String,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val dokumenter: List<DokumentMedTittel>,
    val type: String,
    val source: String = "scanning",
)

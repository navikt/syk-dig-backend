package no.nav.sykdig.nasjonal.kafka

import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.shared.utils.getLoggingMeta
import no.nav.sykdig.gosys.GosysService
import no.nav.sykdig.nasjonal.clients.SmregistreringClient
import no.nav.sykdig.nasjonal.models.*
import no.nav.sykdig.nasjonal.services.NasjonalCommonService
import no.nav.sykdig.nasjonal.services.NasjonalSykmeldingService
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.utenlandsk.services.SykmeldingService
import org.springframework.stereotype.Component
import kotlin.math.log


@Component
class MottaSykmeldingerFraKafka(
    private val metricRegister: MetricRegister,
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val gosysService: GosysService,
    private val smregistreringClient: SmregistreringClient,
    private val nasjonalSykmeldingService: NasjonalSykmeldingService,
) {
    val logger = applog()

    fun behandleNasjonalOppgave(papirSmRegistering: PapirSmRegistering) {
        val loggingMeta = getLoggingMeta(papirSmRegistering.sykmeldingId, papirSmRegistering)
        logger.info("Behandler manuell papirsykmelding for sykmeldingId: {}", StructuredArguments.fields(loggingMeta))
        metricRegister.incoming_message_counter.increment()

        val eksisterendeOppgave = nasjonalOppgaveService.getOppgaveBySykmeldingId(papirSmRegistering.sykmeldingId, "")
        if (eksisterendeOppgave != null) {
            logger.warn(
                "Papirsykmelding med sykmeldingId {} er allerede lagret i databasen. Ingen ny oppgave opprettes.",
                papirSmRegistering.sykmeldingId
            )
            return
        }

        try {
            val oppgave = gosysService.opprettNasjonalOppgave(papirSmRegistering)
            nasjonalOppgaveService.lagreOppgave(papirSmRegistering.toPapirManuellOppgave(oppgave.id))
            logger.info(
                "Manuell papirsykmeldingoppgave lagret i databasen med oppgaveId: {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.id),
                StructuredArguments.fields(loggingMeta)
            )
            metricRegister.message_stored_in_db_counter.increment()
        } catch (ex: Exception) {
            logger.error(
                "Feil ved oppretting av nasjonal oppgave for sykmeldingId: {}. Feilmelding: {}",
                papirSmRegistering.sykmeldingId,
                ex.message,
                ex
            )
        }
    }

    // TODO: kun migrering
    fun lagreISykDig(papirsmregistrering: PapirSmRegistering) {
        val eksisterendeOppgave = nasjonalOppgaveService.getOppgaveBySykmeldingIdSykDig(papirsmregistrering.sykmeldingId, "")
        logger.info("henter eksisterende oppgave fra db for å se om den ligger der ${papirsmregistrering.sykmeldingId}, oppgaveId: ${papirsmregistrering.oppgaveId}, eksisterende: ${eksisterendeOppgave?.sykmeldingId}")
        if (eksisterendeOppgave == null) {
            logger.info("gjør kall mot smreg for å hente oppgave der")
            val oppgaveSmregResponse = smregistreringClient.getOppgaveRequestWithoutAuth(papirsmregistrering.sykmeldingId)
            logger.info("hentet respons fra smreg med sykmeldingId ${papirsmregistrering.sykmeldingId}, respons fra smreg: ${oppgaveSmregResponse.body.first().sykmeldingId}")
            val oppgaveSmreg = oppgaveSmregResponse.body?.firstOrNull()

            if (oppgaveSmreg != null) {
                logger.info("respons smreg med sykmeldingId ${papirsmregistrering.sykmeldingId}, oppgaveSmreg er ikke null")
                val papirManuellOppgave = PapirManuellOppgave(
                    fnr = oppgaveSmreg.fnr,
                    sykmeldingId = oppgaveSmreg.sykmeldingId,
                    oppgaveid = oppgaveSmreg.oppgaveid,
                    pdfPapirSykmelding = oppgaveSmreg.pdfPapirSykmelding ?: ByteArray(0),
                    papirSmRegistering = oppgaveSmreg.papirSmRegistering,
                    documents = listOf(
                        Document(
                            dokumentInfoId = oppgaveSmreg.dokumentInfoId ?: "",
                            tittel = "papirsykmelding"
                        )
                    )
                )
                logger.info("lagrer oppgave med sykmeldingId ${oppgaveSmreg.sykmeldingId}")
                nasjonalOppgaveService.lagreOppgave(papirManuellOppgave, journalpostId =  oppgaveSmreg.journalpostId)
                val sykmeldingerResponse = smregistreringClient.getSykmeldingRequestWithoutAuth(papirsmregistrering.sykmeldingId)
                val sykmeldinger = sykmeldingerResponse.body
                sykmeldinger?.forEach { sykmelding ->
                    val ferdigstiltAv = if (sykmelding.ferdigstiltAv.isBlank()) oppgaveSmreg.ferdigstiltAv ?: "" else sykmelding.ferdigstiltAv
                    nasjonalSykmeldingService.lagreSykmelding(
                        sykmelding.receivedSykmelding,
                        Veileder(ferdigstiltAv),
                        datoFerdigstilt = oppgaveSmreg.datoFerdigstilt
                    )
                }
            }
        }
        behandleNasjonalOppgave(papirsmregistrering)
    }
}
package no.nav.sykdig.nasjonal.kafka

import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.nasjonal.models.toPapirManuellOppgave
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.shared.utils.getLoggingMeta
import no.nav.sykdig.gosys.GosysService
import no.nav.sykdig.nasjonal.clients.SmregistreringClient
import no.nav.sykdig.nasjonal.models.Veileder
import no.nav.sykdig.nasjonal.services.NasjonalSykmeldingService
import no.nav.sykdig.utenlandsk.services.SykmeldingService
import org.springframework.stereotype.Component


@Component
class MottaSykmeldingerFraKafka(
    private val metricRegister: MetricRegister,
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val gosysService: GosysService,
    private val smregistreringClient: SmregistreringClient,
    private val nasjonalSykmeldingService: NasjonalSykmeldingService,
    private val sykmeldingService: SykmeldingService,
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

    fun lagreISykDig(papirsmregistrering: PapirSmRegistering) {
        val eksisterendeOppgave = nasjonalOppgaveService.getOppgaveBySykmeldingId(papirsmregistrering.sykmeldingId, "")
        if (eksisterendeOppgave == null && papirsmregistrering.oppgaveId != null) {
            val oppgaveSmreg = smregistreringClient.getOppgaveRequestWithoutAuth(papirsmregistrering.oppgaveId).body
            if (oppgaveSmreg != null) {
                nasjonalOppgaveService.lagreOppgave(oppgaveSmreg)
            }
            smregistreringClient.getSykmeldingRequestWithoutAuth(papirsmregistrering.sykmeldingId).body?.forEach {
                nasjonalSykmeldingService.lagreSykmelding(it.receivedSykmelding, Veileder(it.ferdigstiltAv))
            }
        }
        behandleNasjonalOppgave(papirsmregistrering)
    }

}
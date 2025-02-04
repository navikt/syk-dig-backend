package no.nav.sykdig.nasjonal.kafka

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    fun behandleNasjonalOppgave(papirSmRegistering: PapirSmRegistering) {
        val loggingMeta = getLoggingMeta(papirSmRegistering.sykmeldingId, papirSmRegistering)
        logger.info("Behandler manuell papirsykmelding for sykmeldingId: {}", StructuredArguments.fields(loggingMeta))
        metricRegister.incoming_message_counter.increment()

        val eksisterendeOppgave = nasjonalOppgaveService.getOppgaveBySykmeldingId(papirSmRegistering.sykmeldingId, "")
        if (eksisterendeOppgave != null) {
            logger.warn(
                "Papirsykmelding med sykmeldingId {} er allerede lagret i databasen. Ingen ny oppgave opprettes.",
                papirSmRegistering.sykmeldingId,
            )
            return
        }

        try {
            val oppgave = gosysService.opprettNasjonalOppgave(papirSmRegistering)
            nasjonalOppgaveService.lagreOppgave(papirSmRegistering.toPapirManuellOppgave(oppgave.id))
            logger.info(
                "Manuell papirsykmeldingoppgave lagret i databasen med oppgaveId: {}, {}",
                StructuredArguments.keyValue("oppgaveId", oppgave.id),
                StructuredArguments.fields(loggingMeta),
            )
            metricRegister.message_stored_in_db_counter.increment()
        } catch (ex: Exception) {
            logger.error(
                "Feil ved oppretting av nasjonal oppgave for sykmeldingId: {}. Feilmelding: {}",
                papirSmRegistering.sykmeldingId,
                ex.message,
                ex,
            )
        }
    }

    // TODO: kun migrering
    fun lagreISykDig(papirsmregistrering: PapirSmRegistering) {
        val eksisterendeOppgave = nasjonalOppgaveService.getOppgaveBySykmeldingIdSykDig(papirsmregistrering.sykmeldingId, "")
        val eksisterendeSykmelding = nasjonalSykmeldingService.findBySykmeldingId(papirsmregistrering.sykmeldingId)
        logger.info("hentet eksisterende oppgave fra db for å se om den ligger der ${papirsmregistrering.sykmeldingId}, oppgaveId: ${papirsmregistrering.oppgaveId}, eksisterende: ${eksisterendeOppgave?.sykmeldingId}")

        if (eksisterendeOppgave != null) {
            logger.info("Sykmelding med id ${papirsmregistrering.sykmeldingId} ligger allerede i syk-dig-db nasjonal_manuelloppgave tabell")
            return
        }
        logger.info("gjør kall mot smreg for å hente oppgaven der ${papirsmregistrering.sykmeldingId}")
        val oppgaveSmregResponse = smregistreringClient.getOppgaveRequestWithoutAuth(papirsmregistrering.sykmeldingId)

        if (oppgaveSmregResponse == null) {
            logger.info("Ingen sykmelding i Smreg på sykmeldingId ${papirsmregistrering.sykmeldingId} $papirsmregistrering")
            //behandleNasjonalOppgave(papirsmregistrering)
            return
        }

        logger.info("hentet respons fra smreg med sykmeldingId ${papirsmregistrering.sykmeldingId}, respons fra smreg: ${oppgaveSmregResponse?.first()?.sykmeldingId}")
        val oppgaveSmreg = oppgaveSmregResponse.first()

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
                    tittel = "papirsykmelding",
                ),
            ),
        )

        logger.info("lagrer oppgave med sykmeldingId i nasjonal_manuelloppgave ${oppgaveSmreg.sykmeldingId}")
        nasjonalOppgaveService.lagreOppgaveMigrering(
            papirManuellOppgave = papirManuellOppgave,
            journalpostId = oppgaveSmreg.journalpostId,
            ferdigstilt = oppgaveSmreg.ferdigstilt,
            aktorId = oppgaveSmreg.aktorId,
            dokumentInfoId = oppgaveSmreg.dokumentInfoId,
            datoOpprettet = oppgaveSmreg.datoOpprettet?.toLocalDateTime(),
            utfall = oppgaveSmreg.utfall,
            ferdigstiltAv = oppgaveSmreg.ferdigstiltAv,
            datoFerdigstilt = oppgaveSmreg.datoFerdigstilt,
            avvisningsgrunn = oppgaveSmreg.avvisningsgrunn,
        )
        if (!eksisterendeSykmelding.isNullOrEmpty()){
            logger.info("Sykmelding med id ${papirsmregistrering.sykmeldingId} ligger allerede i syk-dig-db nasjonal_sykmelding tabell")
            return
        }

        logger.info("henter sykmelding fra sendt_sykmelding og sendt_sykmelding_history tabell i smreg ${oppgaveSmreg.sykmeldingId}")
        val sykmeldingerResponse = smregistreringClient.getSykmeldingRequestWithoutAuth(papirsmregistrering.sykmeldingId)
        if (sykmeldingerResponse.isNullOrEmpty()) {
            logger.info("Ingen sykmelding i Smreg på sykmeldingId ${papirsmregistrering.sykmeldingId} $papirsmregistrering")
            return
        }
        val jsonResponse = objectMapper.writeValueAsString(sykmeldingerResponse)
        logger.info("sykmelding fra smreg ${oppgaveSmreg.sykmeldingId}: $jsonResponse datoferdigstil: ${sykmeldingerResponse?.first()?.datoFerdigstilt}")
        sykmeldingerResponse.forEach { sykmelding ->
            val ferdigstiltAv = if (sykmelding.ferdigstiltAv.isBlank()) oppgaveSmreg.ferdigstiltAv ?: "" else sykmelding.ferdigstiltAv
            nasjonalSykmeldingService.lagreSykmeldingMigrering(
                sykmelding.receivedSykmelding,
                Veileder(ferdigstiltAv),
                datoFerdigstilt = sykmelding.datoFerdigstilt,
                time = sykmelding.timestamp
            )
        }
        //behandleNasjonalOppgave(papirsmregistrering)
    }

}
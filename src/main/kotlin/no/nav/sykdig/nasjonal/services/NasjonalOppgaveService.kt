package no.nav.sykdig.nasjonal.services

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.exceptions.DgsBadRequestException
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.generated.types.LagreNasjonalOppgaveStatus
import no.nav.sykdig.generated.types.LagreNasjonalOppgaveStatusEnum
import no.nav.sykdig.generated.types.LagreOppgaveResult
import no.nav.sykdig.gosys.GosysService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.auditLogger.AuditLogger
import no.nav.sykdig.utenlandsk.api.getPdfResult
import no.nav.sykdig.nasjonal.db.models.Utfall
import no.nav.sykdig.nasjonal.mapping.NasjonalSykmeldingMapper
import no.nav.sykdig.saf.SafClient
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.nasjonal.models.*
import no.nav.sykdig.shared.*
import no.nav.sykdig.shared.utils.getLoggingMeta
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class NasjonalOppgaveService(
    private val nasjonalSykmeldingMapper: NasjonalSykmeldingMapper,
    private val safClient: SafClient,
    private val nasjonalFerdigstillingService: NasjonalFerdigstillingService,
    private val metricRegister: MetricRegister,
    private val gosysService: GosysService,
    private val nasjonalDbService: NasjonalDbService

) {
    val log = applog()
    val securelog = securelog()
    val auditLogger = auditlog()
    val mapper = jacksonObjectMapper()

    val logger = applog()
    val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    fun behandleNasjonalOppgaveFraKafka(papirSmRegistering: PapirSmRegistering) {
        val loggingMeta = getLoggingMeta(papirSmRegistering.sykmeldingId, papirSmRegistering)
        logger.info("Behandler manuell papirsykmelding fra kafka for sykmeldingId: {}", StructuredArguments.fields(loggingMeta))
        metricRegister.incoming_message_counter.increment()

        val eksisterendeOppgave = nasjonalDbService.getOppgaveBySykmeldingId(papirSmRegistering.sykmeldingId)
        if (eksisterendeOppgave != null) {
            logger.warn(
                "Papirsykmelding med sykmeldingId {} er allerede lagret i databasen. Ingen ny oppgave opprettes.",
                papirSmRegistering.sykmeldingId,
            )
            return
        }
        try {
            val oppgave = gosysService.opprettNasjonalOppgave(papirSmRegistering)
            nasjonalDbService.saveOppgave(papirSmRegistering.toPapirManuellOppgave(oppgave.id))
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
            throw ex
        }
    }

    suspend fun korrigerSykmeldingMedOppgaveId(oppgaveId: String, navEnhet: String, callId: String, papirSykmelding: SmRegistreringManuell): LagreOppgaveResult {
        val oppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId) ?: return LagreNasjonalOppgaveStatus(
            oppgaveId,
            status = LagreNasjonalOppgaveStatusEnum.FINNES_IKKE,
        )
        log.info("Forsøker å korrigere sykmelding med oppgaveId $oppgaveId")
        return nasjonalFerdigstillingService.validerOgFerdigstillNasjonalSykmelding(papirSykmelding, navEnhet, callId, oppgave, oppgave.oppgaveId.toString(), LagreNasjonalOppgaveStatusEnum.OPPDATERT)
    }

    suspend fun sendOppgave(papirSykmelding: SmRegistreringManuell, navEnhet: String, callId: String, oppgaveId: String): LagreOppgaveResult {
        securelog.info("sender papirsykmelding med oppgaveId $oppgaveId {}", kv("smregistreringManuell", objectMapper.writeValueAsString(papirSykmelding)))
        val oppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId) ?: return LagreNasjonalOppgaveStatus(
            oppgaveId,
            status = LagreNasjonalOppgaveStatusEnum.FINNES_IKKE,
        )
        if (oppgave.ferdigstilt) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            throw DgsEntityNotFoundException("Oppgave med id $oppgaveId er allerede ferdigstilt")
        }
        log.info("Forsøker å sende inn papirsykmelding med sykmeldingId ${oppgave.sykmeldingId} oppgaveId ${oppgave.oppgaveId}")
        return nasjonalFerdigstillingService.validerOgFerdigstillNasjonalSykmelding(papirSykmelding, navEnhet, callId, oppgave, oppgaveId, LagreNasjonalOppgaveStatusEnum.FERDIGSTILT)
    }

    suspend fun avvisOppgave(
        oppgaveId: String,
        avvisningsgrunn: String?,
        navEnhet: String,
    ): LagreNasjonalOppgaveStatus {
        val lokalOppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId)
        if (lokalOppgave == null) {
            log.info("Fant ikke oppgave som skulle avvises: $oppgaveId")
            throw DgsEntityNotFoundException("Fant ikke oppgave som skulle avvises: $oppgaveId")
        }
        if (lokalOppgave.ferdigstilt) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            throw DgsBadRequestException("Oppgave med id $oppgaveId er allerede ferdigstilt")
        }
        log.info("Avviser oppgave med oppgaveId: $oppgaveId. Avvisningsgrunn: $avvisningsgrunn")
        val veilederIdent = nasjonalSykmeldingMapper.getNavIdent().veilederIdent
        nasjonalFerdigstillingService.ferdigstillNasjonalAvvistOppgave(lokalOppgave, oppgaveId.toInt(), navEnhet, avvisningsgrunn, veilederIdent)

        nasjonalDbService.updateOppgave(
            lokalOppgave.sykmeldingId,
            utfall = Utfall.AVVIST.toString(),
            ferdigstiltAv = veilederIdent,
            avvisningsgrunn = avvisningsgrunn,
            null,
            null,
        )
        auditLogger.info(
            AuditLogger()
                .createcCefMessage(
                    fnr = lokalOppgave.fnr,
                    operation = AuditLogger.Operation.WRITE,
                    requestPath = "/api/v1/oppgave/$oppgaveId/avvis",
                    permit = AuditLogger.Permit.PERMIT,
                    navEmail = nasjonalSykmeldingMapper.getNavEmail(),
                ),
        )

        log.info("Har avvist oppgave med oppgaveId $oppgaveId")
        return LagreNasjonalOppgaveStatus(
            oppgaveId = oppgaveId,
            status = LagreNasjonalOppgaveStatusEnum.AVVIST
        )
    }

    fun getRegisterPdf(oppgaveId: String, dokumentInfoId: String): ResponseEntity<Any> {
        val oppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId)
        requireNotNull(oppgave)
        val pdfResult = safClient.getPdfFraSaf(oppgave.journalpostId, dokumentInfoId, oppgave.sykmeldingId)
        return getPdfResult(pdfResult)
    }

    fun oppgaveTilGosys(oppgaveId: String) {
        val eksisterendeOppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId)
        requireNotNull(eksisterendeOppgave)
        val navIdent = nasjonalSykmeldingMapper.getNavIdent()
        val loggingMeta = getLoggingMeta(eksisterendeOppgave.sykmeldingId, eksisterendeOppgave)
        nasjonalFerdigstillingService.ferdigstillOgSendOppgaveTilGosys(oppgaveId, eksisterendeOppgave)
        nasjonalDbService.updateOppgave(eksisterendeOppgave.sykmeldingId, Utfall.SENDT_TIL_GOSYS.toString(), navIdent.veilederIdent, null, null, null)

        log.info(
            "Ferdig å sende oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta),
        )
        metricRegister.sendtTilGosysNasjonal.increment()
    }
}

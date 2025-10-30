package no.nav.sykdig.nasjonal.services

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.generated.types.LagreNasjonalOppgaveStatus
import no.nav.sykdig.generated.types.LagreNasjonalOppgaveStatusEnum
import no.nav.sykdig.generated.types.LagreOppgaveResult
import no.nav.sykdig.gosys.GosysService
import no.nav.sykdig.gosys.OppgaveClient
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.kafka.NasjonalKafkaService
import no.nav.sykdig.nasjonal.mapping.NasjonalSykmeldingMapper
import no.nav.sykdig.nasjonal.models.FerdigstillRegistrering
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Veileder
import no.nav.sykdig.shared.*
import no.nav.sykdig.shared.utils.getLoggingMeta
import no.nav.sykdig.utenlandsk.services.JournalpostService
import org.springframework.stereotype.Service

@Service
class NasjonalFerdigstillingService(
    private val journalpostService: JournalpostService,
    private val nasjonalSykmeldingMapper: NasjonalSykmeldingMapper,
    private val sykmelderService: SykmelderService,
    private val oppgaveClient: OppgaveClient,
    private val gosysService: GosysService,
    private val nasjonalKafkaService: NasjonalKafkaService,
    private val nasjonalDbService: NasjonalDbService,
    private val nasjonalRegelvalideringService: NasjonalRegelvalideringService,
) {

    val log = applog()
    val securelog = securelog()

    suspend fun validerOgFerdigstillNasjonalSykmelding(
        smRegistreringManuell: SmRegistreringManuell,
        navEnhet: String,
        callId: String,
        oppgave: NasjonalManuellOppgaveDAO,
        oppgaveId: String,
        status: LagreNasjonalOppgaveStatusEnum,
    ): LagreOppgaveResult {
        val sykmeldingId = oppgave.sykmeldingId
        log.info("Forsøker å ferdigstille papirsykmelding med sykmeldingId $sykmeldingId")

        val loggingMeta = getLoggingMeta(sykmeldingId, oppgave)
        val sykmelder =
            sykmelderService.checkHprAndGetSykmelder(smRegistreringManuell, loggingMeta, callId)
        val receivedSykmelding =
            nasjonalSykmeldingMapper.createReceivedSykmelding(
                sykmeldingId,
                oppgave,
                loggingMeta,
                smRegistreringManuell,
                callId,
                sykmelder,
            )
        securelog.info(
            "sender oppgave med id $oppgaveId og navenhet $navEnhet og callId $callId og sykmelder $sykmelder"
        )
        val validationResult =
            nasjonalRegelvalideringService.validerNasjonalSykmelding(
                receivedSykmelding,
                smRegistreringManuell,
                sykmeldingId,
                loggingMeta,
                oppgaveId,
                sykmelder,
            )

        val dokumentInfoId = oppgave.dokumentInfoId
        val journalpostId = oppgave.journalpostId

        val ferdigstillRegistrering =
            FerdigstillRegistrering(
                oppgaveId = oppgave.oppgaveId,
                journalpostId = journalpostId,
                dokumentInfoId = dokumentInfoId,
                pasientFnr = receivedSykmelding.personNrPasient,
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                navEnhet = navEnhet,
                veileder = nasjonalSykmeldingMapper.getNavIdent(),
                avvist = false,
                oppgave = null,
            )

        if (!validationResult.ruleHits.isWhitelisted()) {
            return mapToValidationResult(handleBrokenRule(validationResult, oppgaveId))
        }

        return ferdigstillSykmeldingOk(
            validationResult,
            receivedSykmelding.copy(validationResult = validationResult),
            ferdigstillRegistrering,
            loggingMeta,
            null,
            smRegistreringManuell,
            oppgaveId,
            status,
        )
    }

    suspend fun ferdigstillSykmeldingOk(
        validationResult: ValidationResult,
        receivedSykmelding: ReceivedSykmelding,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta,
        avvisningsgrunn: String?,
        smRegistreringManuell: SmRegistreringManuell,
        oppgaveId: String,
        status: LagreNasjonalOppgaveStatusEnum,
    ): LagreOppgaveResult {
        if (
            validationResult.status == Status.OK ||
                validationResult.status == Status.MANUAL_PROCESSING
        ) {
            val veileder = nasjonalSykmeldingMapper.getNavIdent()
            log.info(
                "oppgave er ok, skal ferdigstille i dokarkiv og oppgave {}",
                StructuredArguments.fields(loggingMeta),
            )
            if (ferdigstillRegistrering.oppgaveId != null) {
                journalpostService.ferdigstillNasjonalJournalpost(
                    ferdigstillRegistrering = ferdigstillRegistrering,
                    perioder = receivedSykmelding.sykmelding.perioder,
                    loggingMeta = loggingMeta,
                )
                ferdigstillOppgave(
                    ferdigstillRegistrering,
                    null,
                    loggingMeta,
                    ferdigstillRegistrering.oppgaveId.toString(),
                )
            }
            nasjonalKafkaService.sendSykmeldingToKafka(receivedSykmelding)
            securelog.info("receivedSykmelding som skal lagres: ${receivedSykmelding}")
            nasjonalDbService.saveSykmelding(receivedSykmelding, veileder)
            log.info(
                "Sykmelding saved to db, nasjonal_sykmelding table {}",
                receivedSykmelding.sykmelding.id,
            )
            nasjonalDbService.updateOppgave(
                sykmeldingId = receivedSykmelding.sykmelding.id,
                utfall = validationResult.status.toString(),
                ferdigstiltAv = veileder.veilederIdent,
                avvisningsgrunn = avvisningsgrunn,
                smRegistreringManuell = smRegistreringManuell,
                utdypendeOpplysninger = receivedSykmelding.sykmelding.utdypendeOpplysninger,
            )
            log.info(
                "Ferdigstilt papirsykmelding med sykmelding id ${receivedSykmelding.sykmelding.id}"
            )
            return LagreNasjonalOppgaveStatus(oppgaveId, status = status)
        }
        log.error(
            "Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING"
        )
        throw Exception("Ukjent status: ${validationResult.status}")
    }

    suspend fun ferdigstillNasjonalAvvistOppgave(
        lokalOppgave: NasjonalManuellOppgaveDAO,
        oppgaveId: Int,
        navEnhet: String,
        avvisningsgrunn: String?,
        veilederIdent: String,
    ) {
        val eksternOppgave =
            oppgaveClient.getNasjonalOppgave(oppgaveId.toString(), lokalOppgave.sykmeldingId)
        val sykmeldingId = lokalOppgave.sykmeldingId
        val loggingMeta = getLoggingMeta(lokalOppgave.sykmeldingId, lokalOppgave)
        requireNotNull(lokalOppgave.oppgaveId)
        val sykmelder =
            sykmelderService.getSykmelderForAvvistOppgave(
                lokalOppgave.papirSmRegistrering.behandler?.hpr,
                lokalOppgave.sykmeldingId,
                lokalOppgave.oppgaveId,
            )

        requireNotNull(lokalOppgave.fnr)
        val ferdigstillRegistrering =
            FerdigstillRegistrering(
                oppgaveId = oppgaveId,
                journalpostId = lokalOppgave.journalpostId,
                dokumentInfoId = lokalOppgave.dokumentInfoId,
                pasientFnr = lokalOppgave.fnr,
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                navEnhet = navEnhet,
                veileder = Veileder(veilederIdent),
                avvist = true,
                oppgave = eksternOppgave,
            )
        journalpostService.ferdigstillNasjonalJournalpost(
            ferdigstillRegistrering,
            lokalOppgave.papirSmRegistrering.perioder,
            loggingMeta,
        )
        ferdigstillOppgave(
            ferdigstillRegistrering = ferdigstillRegistrering,
            beskrivelse =
                lagAvvisOppgavebeskrivelse(
                    opprinneligBeskrivelse = eksternOppgave.beskrivelse,
                    veileder = Veileder(veilederIdent),
                    navEnhet = navEnhet,
                    avvisSykmeldingReason = avvisningsgrunn,
                ),
            loggingMeta = loggingMeta,
            oppgaveId = oppgaveId.toString(),
        )
    }

    // TODO dobbelsjekk endretAvEnhetsnr her
    private suspend fun ferdigstillOppgave(
        ferdigstillRegistrering: FerdigstillRegistrering,
        beskrivelse: String?,
        loggingMeta: LoggingMeta,
        oppgaveId: String,
    ) {
        oppgaveClient.ferdigstillNasjonalOppgave(
            oppgaveId,
            ferdigstillRegistrering.sykmeldingId,
            ferdigstillRegistrering,
            loggingMeta,
            beskrivelse,
            ferdigstillRegistrering.navEnhet,
        )
    }

    private fun handleBrokenRule(
        validationResult: ValidationResult,
        oppgaveId: String,
    ): ValidationResult {
        if (validationResult.status == Status.MANUAL_PROCESSING) {
            log.info(
                "Ferdigstilling av nasjonal sykmelding traff regel MANUAL_PROCESSING {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),
            )
            return validationResult
        }
        if (validationResult.status == Status.OK) {
            log.info(
                "Ferdigstilling av papirsykmeldinger manuell registering traff regel OK {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),
            )
            return validationResult
        }
        log.error(
            "Ukjent status: ${validationResult.status}, nasjonal sykmelding kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING"
        )
        throw Exception(
            "En uforutsett feil oppsto ved validering av oppgaven med oppgaveId: $oppgaveId"
        )
    }

    fun lagAvvisOppgavebeskrivelse(
        avvisSykmeldingReason: String?,
        opprinneligBeskrivelse: String?,
        veileder: Veileder,
        navEnhet: String,
        timestamp: LocalDateTime? = null,
    ): String {
        val oppdatertBeskrivelse =
            when {
                !avvisSykmeldingReason.isNullOrEmpty() ->
                    "Avvist papirsykmelding med årsak: $avvisSykmeldingReason"

                else -> "Avvist papirsykmelding uten oppgitt årsak."
            }
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val formattedTimestamp = (timestamp ?: LocalDateTime.now()).format(formatter)
        return "--- $formattedTimestamp ${veileder.veilederIdent}, $navEnhet ---\n$oppdatertBeskrivelse\n\n$opprinneligBeskrivelse"
    }

    fun ferdigstillOgSendOppgaveTilGosys(
        oppgaveId: String,
        navEnhet: String,
        eksisterendeOppgave: NasjonalManuellOppgaveDAO,
    ) {
        val sykmeldingId = eksisterendeOppgave.sykmeldingId
        val loggingMeta = getLoggingMeta(sykmeldingId, eksisterendeOppgave)
        log.info(
            "Sender nasjonal oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta),
        )
        val navIdent = nasjonalSykmeldingMapper.getNavIdent().veilederIdent
        gosysService.sendNasjonalOppgaveTilGosys(
            oppgaveId = oppgaveId,
            sykmeldingId = sykmeldingId,
            veilederNavIdent = navIdent,
            endretAvEnhetsnr = navEnhet,
        )
    }

    fun mapToValidationResult(
        validationResult: ValidationResult
    ): no.nav.sykdig.generated.types.ValidationResult {
        return no.nav.sykdig.generated.types.ValidationResult(
            status = mapToStatus(validationResult.status),
            ruleHits = validationResult.ruleHits.map { mapToRuleInfo(it) },
        )
    }

    fun mapToStatus(status: Status): no.nav.sykdig.generated.types.Status {
        return when (status) {
            Status.MANUAL_PROCESSING -> no.nav.sykdig.generated.types.Status.MANUAL_PROCESSING
            Status.OK -> no.nav.sykdig.generated.types.Status.OK
            Status.INVALID -> no.nav.sykdig.generated.types.Status.INVALID
        }
    }

    fun mapToRuleInfo(ruleInfo: RuleInfo): no.nav.sykdig.generated.types.RuleInfo {
        return no.nav.sykdig.generated.types.RuleInfo(
            ruleName = ruleInfo.ruleName,
            messageForSender = ruleInfo.messageForSender,
            messageForUser = ruleInfo.messageForUser,
            ruleStatus = mapToStatus(ruleInfo.ruleStatus),
        )
    }
}

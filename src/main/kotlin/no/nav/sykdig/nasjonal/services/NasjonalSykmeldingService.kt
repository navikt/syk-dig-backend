package no.nav.sykdig.nasjonal.services

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.generated.types.NasjonalOppgaveStatus
import no.nav.sykdig.generated.types.NasjonalOppgaveStatusEnum
import no.nav.sykdig.generated.types.LagreSykmeldingResult
import no.nav.sykdig.generated.types.ValidationResult
import no.nav.sykdig.generated.types.Status
import no.nav.sykdig.generated.types.RuleInfo
import no.nav.sykdig.nasjonal.db.NasjonalSykmeldingRepository
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.db.models.NasjonalSykmeldingDAO
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.models.FerdigstillRegistrering
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.nasjonal.models.Veileder
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.ReceivedSykmelding
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.config.kafka.OK_SYKMELDING_TOPIC
import no.nav.sykdig.shared.exceptions.SykmelderNotFoundException
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.shared.utils.getLoggingMeta
import no.nav.sykdig.utenlandsk.services.JournalpostService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class NasjonalSykmeldingService(
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val nasjonalSykmeldingRepository: NasjonalSykmeldingRepository,
    private val journalpostService: JournalpostService,
    private val sykmeldingOKProducer: KafkaProducer<String, ReceivedSykmelding>,
    private val sykmelderService: SykmelderService,
    private val nasjonalCommonService: NasjonalCommonService,
    private val nasjonalFerdigstillingsService: NasjonalFerdigstillingsService,
    private val nasjonalRegelvalideringService: NasjonalRegelvalideringService,
) {
    val log = applog()
    val securelog = securelog()
    val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    suspend fun korrigerSykmeldingMedOppgaveId(oppgaveId: String, navEnhet: String, callId: String, papirSykmelding: SmRegistreringManuell): LagreSykmeldingResult {
        val oppgave = nasjonalOppgaveService.getOppgave(oppgaveId) ?: return NasjonalOppgaveStatus(
            oppgaveId,
            status = NasjonalOppgaveStatusEnum.FINNES_IKKE,
        )
        log.info("Forsøker å korrigere sykmelding med oppgaveId $oppgaveId, graphql")
        return sendPapirsykmeldingGraphql(papirSykmelding, navEnhet, callId, oppgave, oppgaveId)
    }

    suspend fun sendPapirsykmeldingOppgaveGraphql(papirSykmelding: SmRegistreringManuell, navEnhet: String, callId: String, oppgaveId: String): LagreSykmeldingResult {
        securelog.info("sender papirsykmelding med oppgaveId $oppgaveId {}", kv("smregistreringManuell", objectMapper.writeValueAsString(papirSykmelding)))
        val oppgave = nasjonalOppgaveService.getOppgave(oppgaveId) ?: return NasjonalOppgaveStatus(
            oppgaveId,
            status = NasjonalOppgaveStatusEnum.FINNES_IKKE,
        )
        if (oppgave.ferdigstilt) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            return NasjonalOppgaveStatus(
                oppgaveId,
                status = NasjonalOppgaveStatusEnum.FERDIGSTILT,
            )
        }
        log.info("Forsøker å sende inn papirsykmelding med sykmeldingId ${oppgave.sykmeldingId} oppgaveId ${oppgave.oppgaveId}, graphql")
        return sendPapirsykmeldingGraphql(papirSykmelding, navEnhet, callId, oppgave, oppgaveId)
    }

    suspend fun sendPapirsykmeldingGraphql(smRegistreringManuell: SmRegistreringManuell, navEnhet: String, callId: String, oppgave: NasjonalManuellOppgaveDAO, oppgaveId: String): LagreSykmeldingResult {
        val sykmeldingId = oppgave.sykmeldingId
        log.info("Forsøker å ferdigstille papirsykmelding med sykmeldingId $sykmeldingId, graphql")

        val loggingMeta = getLoggingMeta(sykmeldingId, oppgave)
        val sykmelder = getSykmelder(smRegistreringManuell, loggingMeta, callId)
        val receivedSykmelding = nasjonalCommonService.createReceivedSykmelding(sykmeldingId, oppgave, loggingMeta, smRegistreringManuell, callId, sykmelder)
        securelog.info("sender oppgave med id $oppgaveId og navenhet $navEnhet og callId $callId og sykmelder $sykmelder")
        val validationResult = nasjonalRegelvalideringService.validerNasjonalSykmelding(receivedSykmelding, smRegistreringManuell, sykmeldingId, loggingMeta, oppgaveId, sykmelder)

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
                veileder = nasjonalCommonService.getNavIdent(),
                avvist = false,
                oppgave = null,
            )

        if (!validationResult.ruleHits.isWhitelisted()) {
            return mapToValidationResult(handleBrokenRule(validationResult, oppgave.oppgaveId))
        }

        handleOK(validationResult, receivedSykmelding.copy(validationResult = validationResult), ferdigstillRegistrering, loggingMeta, smRegistreringManuell)
        return NasjonalOppgaveStatus(
            oppgaveId,
            status = NasjonalOppgaveStatusEnum.FERDIGSTILT,
        )
    }

    //TODO: remove after merge
    suspend fun korrigerSykmelding(sykmeldingId: String, navEnhet: String, callId: String, papirSykmelding: SmRegistreringManuell): ResponseEntity<Any> {
        val oppgave = nasjonalOppgaveService.findBySykmeldingId(sykmeldingId) ?: return ResponseEntity(HttpStatus.NOT_FOUND)
        log.info("Forsøker å korriger sykmelding med sykmeldingId $sykmeldingId og oppgaveId ${oppgave.oppgaveId}")
        return sendPapirsykmelding(papirSykmelding, navEnhet, callId, oppgave, oppgave.oppgaveId.toString())
    }

    //TODO: remove after merge
    suspend fun sendPapirsykmeldingOppgave(papirSykmelding: SmRegistreringManuell, navEnhet: String, callId: String, oppgaveId: String): ResponseEntity<Any> {
        securelog.info("sender papirsykmelding med oppgaveId $oppgaveId {}", kv("smregistreringManuell", objectMapper.writeValueAsString(papirSykmelding)))
        val oppgave = nasjonalOppgaveService.getOppgave(oppgaveId) ?: return ResponseEntity(HttpStatus.NOT_FOUND)
        if (oppgave.ferdigstilt) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            return ResponseEntity(HttpStatus.NO_CONTENT)
        }
        log.info("Forsøker å sende inn papirsykmelding med sykmeldingId ${oppgave.sykmeldingId} oppgaveId ${oppgave.oppgaveId}")
        return sendPapirsykmelding(papirSykmelding, navEnhet, callId, oppgave, oppgaveId)
    }

    //TODO: remove after merge
    suspend fun sendPapirsykmelding(smRegistreringManuell: SmRegistreringManuell, navEnhet: String, callId: String, oppgave: NasjonalManuellOppgaveDAO, oppgaveId: String): ResponseEntity<Any> {
        val sykmeldingId = oppgave.sykmeldingId
        log.info("Forsøker å ferdigstille papirsykmelding med sykmeldingId $sykmeldingId")

        val loggingMeta = getLoggingMeta(sykmeldingId, oppgave)
        val sykmelder = getSykmelder(smRegistreringManuell, loggingMeta, callId)
        val receivedSykmelding = nasjonalCommonService.createReceivedSykmelding(sykmeldingId, oppgave, loggingMeta, smRegistreringManuell, callId, sykmelder)
        securelog.info("sender oppgave med id $oppgaveId og navenhet $navEnhet og callId $callId og sykmelder $sykmelder")
        val validationResult = nasjonalRegelvalideringService.validerNasjonalSykmelding(receivedSykmelding, smRegistreringManuell, sykmeldingId, loggingMeta, oppgaveId, sykmelder)

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
                veileder = nasjonalCommonService.getNavIdent(),
                avvist = false,
                oppgave = null,
            )

        if (!validationResult.ruleHits.isWhitelisted()) {
            return ResponseEntity.ok().body(handleBrokenRule(validationResult, oppgave.oppgaveId))
        }
        return ResponseEntity.ok().body(handleOK(validationResult, receivedSykmelding.copy(validationResult = validationResult), ferdigstillRegistrering, loggingMeta, smRegistreringManuell))
    }

    suspend fun handleOK(
        validationResult: no.nav.sykdig.shared.ValidationResult,
        receivedSykmelding: ReceivedSykmelding,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta,
        smRegistreringManuell: SmRegistreringManuell,
    ): Unit? {
        if (validationResult.status == no.nav.sykdig.shared.Status.OK || validationResult.status == no.nav.sykdig.shared.Status.MANUAL_PROCESSING) {
            val veileder = nasjonalCommonService.getNavIdent()
            log.info("oppgave er ok, skal ferdigstille i dokarkiv og oppgave {}", StructuredArguments.fields(loggingMeta))
            if (ferdigstillRegistrering.oppgaveId != null) {
                journalpostService.ferdigstillNasjonalJournalpost(
                    ferdigstillRegistrering = ferdigstillRegistrering,
                    perioder = receivedSykmelding.sykmelding.perioder,
                    loggingMeta = loggingMeta,
                )
                nasjonalFerdigstillingsService.ferdigstillOppgave(
                    ferdigstillRegistrering,
                    null,
                    loggingMeta,
                    ferdigstillRegistrering.oppgaveId.toString(),
                )
            }
            insertSykmeldingAndSendToKafka(receivedSykmelding, veileder)
            nasjonalOppgaveService.oppdaterOppgave(
                sykmeldingId = receivedSykmelding.sykmelding.id,
                utfall = validationResult.status.toString(),
                ferdigstiltAv = veileder.veilederIdent,
                avvisningsgrunn = null,
                smRegistreringManuell = smRegistreringManuell,
            )
            log.info("Ferdigstilt papirsykmelding med sykmelding id ${receivedSykmelding.sykmelding.id}")
            return null
        }
        log.error(
            "Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING",
        )
        throw Exception("Ukjent status: ${validationResult.status}")
    }

    private fun insertSykmeldingAndSendToKafka(
        receivedSykmelding: ReceivedSykmelding,
        veileder: Veileder,
    ) {
        try {
            sykmeldingOKProducer.send(
                ProducerRecord(OK_SYKMELDING_TOPIC, receivedSykmelding.sykmelding.id, receivedSykmelding),
            ).get()
            log.info(
                "Sykmelding sendt to kafka topic {} sykmelding id {}",
                OK_SYKMELDING_TOPIC,
                receivedSykmelding.sykmelding.id,
            )
        } catch (exception: Exception) {
            log.error("failed to send sykmelding to kafka result for sykmeldingId: {}", receivedSykmelding.sykmelding.id)
            throw exception
        }
        securelog.info("receivedSykmelding som skal lagres: ${receivedSykmelding}")
        lagreSykmelding(receivedSykmelding, veileder)
        log.info("Sykmelding saved to db, nasjonal_sykmelding table {}", receivedSykmelding.sykmelding.id)
    }

    fun lagreSykmelding(receivedSykmelding: ReceivedSykmelding, veileder: Veileder) {
        val dao = mapToDao(
            receivedSykmelding = receivedSykmelding, veileder = veileder,
            datoFerdigstilt = OffsetDateTime.now(),
            timestamp = OffsetDateTime.now(),
        )
        nasjonalSykmeldingRepository.save(dao)
    }

    private fun handleBrokenRule(
        validationResult: no.nav.sykdig.shared.ValidationResult,
        oppgaveId: Int?,
    ): no.nav.sykdig.shared.ValidationResult {
        if (validationResult.status == no.nav.sykdig.shared.Status.MANUAL_PROCESSING) {
            log.info(
                "Ferdigstilling av nasjonal sykmelding traff regel MANUAL_PROCESSING {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),
            )
            return validationResult
        }
        if (validationResult.status == no.nav.sykdig.shared.Status.OK) {
            log.info(
                "Ferdigstilling av nasjonal sykmelding traff regel OK {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),
            )
            return validationResult
        }
        log.error("Ukjent status: ${validationResult.status}, nasjonal sykmelding kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
        throw Exception("En uforutsett feil oppsto ved validering av oppgaven med oppgaveId: $oppgaveId")
    }

    private fun getSykmelder(smRegistreringManuell: SmRegistreringManuell, loggingMeta: LoggingMeta, callId: String): Sykmelder {
        val sykmelderHpr = smRegistreringManuell.behandler.hpr
        if (sykmelderHpr.isNullOrEmpty() || sykmelderHpr.isBlank()) {
            log.error("HPR-nummer mangler {}", StructuredArguments.fields(loggingMeta))
            throw SykmelderNotFoundException("HPR-nummer mangler") // dobbeltsjekk at det blir rett å throwe, returnerte bad request før
        }

        log.info("Henter sykmelder fra HPR og PDL med hpr: $sykmelderHpr {}", StructuredArguments.fields(loggingMeta))
        val sykmelder = sykmelderService.getSykmelder(
            sykmelderHpr,
            callId,
        )
        return sykmelder
    }

    fun deleteSykmelding(sykmeldingId: String): Int {
        return nasjonalSykmeldingRepository.deleteBySykmeldingId(sykmeldingId)
    }

    fun mapToDao(
        receivedSykmelding: ReceivedSykmelding,
        veileder: Veileder,
        datoFerdigstilt: OffsetDateTime?,
        timestamp: OffsetDateTime,
    ): NasjonalSykmeldingDAO {
        val mapper = jacksonObjectMapper()
        mapper.registerModules(JavaTimeModule())
        val nasjonalManuellOppgaveDAO =
            NasjonalSykmeldingDAO(
                sykmeldingId = receivedSykmelding.sykmelding.id,
                sykmelding = receivedSykmelding.sykmelding,
                timestamp = timestamp,
                ferdigstiltAv = veileder.veilederIdent,
                datoFerdigstilt = datoFerdigstilt,
            )
        return nasjonalManuellOppgaveDAO
    }

    fun mapToValidationResult(validationResult: no.nav.sykdig.shared.ValidationResult): ValidationResult {
        return ValidationResult(
            status = mapToStatus(validationResult.status),
            ruleHits = validationResult.ruleHits.map { mapToRuleInfo(it) }
        )
    }

    fun mapToStatus(status: no.nav.sykdig.shared.Status): Status {
        return when (status) {
            no.nav.sykdig.shared.Status.MANUAL_PROCESSING -> Status.MANUAL_PROCESSING
            no.nav.sykdig.shared.Status.OK -> Status.OK
            no.nav.sykdig.shared.Status.INVALID -> Status.INVALID
        }
    }

    fun mapToRuleInfo(
        ruleInfo: no.nav.sykdig.shared.RuleInfo,
    ): RuleInfo {
        return RuleInfo(
            ruleName = ruleInfo.ruleName,
            messageForSender = ruleInfo.messageForSender,
            messageForUser = ruleInfo.messageForUser,
            ruleStatus = mapToStatus(ruleInfo.ruleStatus),
        )
    }
}

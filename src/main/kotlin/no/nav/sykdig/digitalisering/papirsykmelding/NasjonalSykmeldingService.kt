package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.config.kafka.OK_SYKMELDING_TOPIC
import no.nav.sykdig.digitalisering.exceptions.SykmelderNotFoundException
import no.nav.sykdig.digitalisering.felles.Sykmelding
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.api.RegelClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.FerdigstillRegistrering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Sykmelder
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Veileder
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.checkValidState
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalSykmeldingRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalSykmeldingDAO
import no.nav.sykdig.digitalisering.sykmelding.ReceivedSykmelding
import no.nav.sykdig.digitalisering.sykmelding.Status
import no.nav.sykdig.digitalisering.sykmelding.ValidationResult
import no.nav.sykdig.digitalisering.sykmelding.service.JournalpostService
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.securelog
import no.nav.sykdig.utils.isWhitelisted
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class NasjonalSykmeldingService(
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val nasjonalSykmeldingRepository: NasjonalSykmeldingRepository,
    private val regelClient: RegelClient,
    private val journalpostService: JournalpostService,
    private val sykmeldingOKProducer: KafkaProducer<String, ReceivedSykmelding>,
    private val sykmelderService: SykmelderService,
    private val oppgaveSecurityService: OppgaveSecurityService,
    private val nasjonalCommon: NasjonalCommon,
) {
    val log = applog()
    val securelog = securelog()


    private fun getLoggingMeta(sykmeldingId: String, oppgave: NasjonalManuellOppgaveDAO): LoggingMeta {
        return LoggingMeta(
            mottakId = sykmeldingId,
            dokumentInfoId = oppgave.dokumentInfoId,
            msgId = sykmeldingId,
            sykmeldingId = sykmeldingId,
            journalpostId = oppgave.journalpostId,
        )
    }

    suspend fun sendPapirsykmelding(smRegistreringManuell: SmRegistreringManuell, navEnhet: String, callId: String, oppgaveId: String): ResponseEntity<Any> {
        val oppgave = nasjonalOppgaveService.findByOppgaveId(oppgaveId) ?: return ResponseEntity(HttpStatus.NOT_FOUND)
        val sykmeldingId = oppgave.sykmeldingId
        log.info("Forsøker å ferdigstille papirsykmelding med sykmeldingId $sykmeldingId")

        val loggingMeta = getLoggingMeta(sykmeldingId, oppgave)
        val sykmelder = getSykmelder(smRegistreringManuell, loggingMeta, callId)
        val receivedSykmelding = nasjonalCommon.createReceivedSykmelding(sykmeldingId, oppgave, loggingMeta, smRegistreringManuell, callId, sykmelder)

        val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
        log.info(
            "Resultat: {}, {}, {}",
            StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
            StructuredArguments.keyValue(
                "ruleHits",
                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName },
            ),
            StructuredArguments.fields(loggingMeta),
        )
        checkValidState(smRegistreringManuell, sykmelder, validationResult)

        val dokumentInfoId = oppgave.dokumentInfoId
        val journalpostId = oppgave.journalpostId

        val ferdigstillRegistrering =
            FerdigstillRegistrering(
                oppgaveId = oppgaveId.toInt(),
                journalpostId = journalpostId,
                dokumentInfoId = dokumentInfoId,
                pasientFnr = receivedSykmelding.personNrPasient,
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                navEnhet = navEnhet,
                veileder = Veileder(oppgaveSecurityService.getNavEmail()),
                avvist = false,
                oppgave = null,
            )

        if (!validationResult.ruleHits.isWhitelisted()) {
            return handleBrokenRule(validationResult, oppgaveId.toInt())
        }

        return handleOK(validationResult, receivedSykmelding.copy(validationResult = validationResult), ferdigstillRegistrering, loggingMeta, null)
    }

    private suspend fun handleOK(
        validationResult: ValidationResult,
        receivedSykmelding: ReceivedSykmelding,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta,
        avvisningsgrunn: String?,
    ): ResponseEntity<Any> {
        if (validationResult.status == Status.OK || validationResult.status == Status.MANUAL_PROCESSING) {
            val veileder = Veileder(oppgaveSecurityService.getNavEmail())
            if (ferdigstillRegistrering.oppgaveId != null) {
                journalpostService.ferdigstillNasjonalJournalpost(
                    ferdigstillRegistrering = ferdigstillRegistrering,
                    receivedSykmelding = receivedSykmelding,
                    loggingMeta = loggingMeta,
                )
                nasjonalOppgaveService.ferdigstillOppgave(
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
                avvisningsgrunn = avvisningsgrunn,
            )
            log.info("Ferdigstilt papirsykmelding med sykmelding id ${receivedSykmelding.sykmelding.id}")
            return ResponseEntity(HttpStatus.OK)
        }
        log.error(
            "Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING",
        )
        return ResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR)
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
        val dao = mapToDao(receivedSykmelding, veileder)
        nasjonalSykmeldingRepository.save(dao)
        log.info("Sykmelding saved to db, nasjonal_sykmelding table {}", receivedSykmelding.sykmelding.id)
    }

    private fun handleBrokenRule(
        validationResult: ValidationResult,
        oppgaveId: Int?,
    ): ResponseEntity<Any> {
        if (validationResult.status == Status.MANUAL_PROCESSING) {
            log.info(
                "Ferdigstilling av papirsykmeldinger manuell registering traff regel MANUAL_PROCESSING {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),
            )
            return ResponseEntity.badRequest().body(validationResult)
        }
        if (validationResult.status == Status.OK) {
            log.info(
                "Ferdigstilling av papirsykmeldinger manuell registering traff regel MANUAL_PROCESSING {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),
            )
            return ResponseEntity.badRequest().body(validationResult)
        }
        log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING")
        return ResponseEntity.internalServerError().body("En uforutsett feil oppsto ved validering av oppgaven")
    }

    private suspend fun getSykmelder(smRegistreringManuell: SmRegistreringManuell, loggingMeta: LoggingMeta, callId: String): Sykmelder {
        val sykmelderHpr = smRegistreringManuell.behandler.hpr
        if (sykmelderHpr.isNullOrEmpty()) {
            log.error("HPR-nummer mangler {}", StructuredArguments.fields(loggingMeta))
            throw SykmelderNotFoundException("HPR-nummer mangler") // dobbeltsjekk at det blir rett å throwe, returnerte bad request før
        }

        log.info("Henter sykmelder fra HPR og PDL")
        val sykmelder = sykmelderService.getSykmelder(
            sykmelderHpr,
            callId,
        )
        return sykmelder
    }


    fun mapToDao(
        receivedSykmelding: ReceivedSykmelding,
        veileder: Veileder,
    ): NasjonalSykmeldingDAO {
        val mapper = jacksonObjectMapper()
        mapper.registerModules(JavaTimeModule())
        val nasjonalManuellOppgaveDAO =
            NasjonalSykmeldingDAO(
                sykmeldingId = receivedSykmelding.sykmelding.id,
                sykmelding = Sykmelding(
                    id = receivedSykmelding.sykmelding.id,
                    msgId = receivedSykmelding.sykmelding.msgId,
                    pasientAktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
                    medisinskVurdering = receivedSykmelding.sykmelding.medisinskVurdering,
                    skjermesForPasient = receivedSykmelding.sykmelding.skjermesForPasient,
                    arbeidsgiver = receivedSykmelding.sykmelding.arbeidsgiver,
                    perioder = receivedSykmelding.sykmelding.perioder,
                    prognose = receivedSykmelding.sykmelding.prognose,
                    utdypendeOpplysninger = receivedSykmelding.sykmelding.utdypendeOpplysninger,
                    tiltakArbeidsplassen = receivedSykmelding.sykmelding.tiltakArbeidsplassen,
                    tiltakNAV = receivedSykmelding.sykmelding.tiltakNAV,
                    andreTiltak = receivedSykmelding.sykmelding.andreTiltak,
                    meldingTilNAV = receivedSykmelding.sykmelding.meldingTilNAV,
                    meldingTilArbeidsgiver = receivedSykmelding.sykmelding.meldingTilArbeidsgiver,
                    kontaktMedPasient = receivedSykmelding.sykmelding.kontaktMedPasient,
                    behandletTidspunkt = receivedSykmelding.sykmelding.behandletTidspunkt,
                    behandler = receivedSykmelding.sykmelding.behandler,
                    avsenderSystem = receivedSykmelding.sykmelding.avsenderSystem,
                    syketilfelleStartDato = receivedSykmelding.sykmelding.syketilfelleStartDato,
                    signaturDato = receivedSykmelding.sykmelding.signaturDato,
                    navnFastlege = receivedSykmelding.sykmelding.navnFastlege
                ),
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                ferdigstiltAv = veileder.veilederIdent,
                datoFerdigstilt = LocalDateTime.now(ZoneOffset.UTC),
            )
        return nasjonalManuellOppgaveDAO
    }
}

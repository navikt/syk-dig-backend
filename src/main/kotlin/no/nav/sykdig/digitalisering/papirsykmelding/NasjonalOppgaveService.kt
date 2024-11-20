package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.service.toSykmelding
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.auditLogger.AuditLogger
import no.nav.sykdig.digitalisering.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.exceptions.SykmelderNotFoundException
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.extractHelseOpplysningerArbeidsuforhet
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.fellesformatMarshaller
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.get
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.toString
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.api.RegelClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.*
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.ReceivedSykmeldingNasjonal
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.sykmelding.Merknad
import no.nav.sykdig.digitalisering.sykmelding.Status
import no.nav.sykdig.digitalisering.sykmelding.ValidationResult
import no.nav.sykdig.digitalisering.sykmelding.service.JournalpostService
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.felles.Sykmelding
import no.nav.sykdig.securelog
import no.nav.sykdig.utils.getLocalDateTime
import no.nav.sykdig.utils.isWhitelisted
import no.nav.sykdig.utils.mapsmRegistreringManuelltTilFellesformat
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val personService: PersonService,
    private val sykmelderService: SykmelderService,
    private val regelClient: RegelClient,
    private val oppgaveSecurityService: OppgaveSecurityService,
    private val journalpostService: JournalpostService,
) {
    val log = applog()
    val securelog = securelog()

    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        if (eksisterendeOppgave.isPresent) {
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.get().id))
        }
        return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null))
    }


    private fun getLoggingMeta(sykmeldingId: String, oppgave: NasjonalManuellOppgaveDAO): LoggingMeta {
        return LoggingMeta(
            mottakId = sykmeldingId,
            dokumentInfoId = oppgave.dokumentInfoId,
            msgId = sykmeldingId,
            sykmeldingId = sykmeldingId,
            journalpostId = oppgave.journalpostId,
        )
    }

    // usikker på hva som skal returneres her
    suspend fun sendPapirsykmelding(smRegistreringManuell: SmRegistreringManuell, navEnhet: String, callId: String, oppgaveId: Int): ResponseEntity<Any> {
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId)
        if (!oppgave.isPresent) return ResponseEntity(HttpStatus.NOT_FOUND) // TODO: bedre error handeling
        val sykmeldingId = oppgave.get().sykmeldingId

        val loggingMeta = getLoggingMeta(sykmeldingId, oppgave.get())
        val sykmelder = getSykmelder(smRegistreringManuell, loggingMeta, callId)

        val receivedSykmelding = createReceivedSykmelding(sykmeldingId, oppgave.get(), loggingMeta, smRegistreringManuell, callId, sykmelder)

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

        val dokumentInfoId = oppgave.get().dokumentInfoId
        val journalpostId = oppgave.get().journalpostId

        val ferdigstillRegistrering =
            FerdigstillRegistrering(
                oppgaveId = oppgaveId,
                journalpostId = journalpostId,
                dokumentInfoId = dokumentInfoId,
                pasientFnr = receivedSykmelding.personNrPasient,
                sykmeldingId = sykmeldingId,
                sykmelder = sykmelder,
                navEnhet = navEnhet,
                veileder = oppgaveSecurityService.getNavIdent(),
                avvist = false,
                oppgave = null,
            )



        // her var det auditlog - dette gjør vi allerede i controlleren når vi sjekker hasaccess.

        if (validationResult.ruleHits.isWhitelisted()){
            return handleOK(validationResult, receivedSykmelding, ferdigstillRegistrering, loggingMeta)
        }
        return handleBrokenRule(validationResult, oppgaveId)








        // logging meta sykmeldingId, dokumentInfoId, journalpostId

        // sender med et isUpdate - som sjekker om saksbehandler har superuseraccess. men dette brukes kun i endre, som ikke har blitt brukt

        // sjekker om saksbehandler har tilgang, men jeg tror dette kan gjøres gjennom obo token i controlleren

        // val sykmelderHpr
        // val sykmelder = personServise.hentPerson(sykmeldderHpr)   -- callId er en randomUUID - spørre seg om kanskje ha callId som sykmeldingId




    }

    private suspend fun handleOK(
        validationResult: ValidationResult,
        receivedSykmelding: ReceivedSykmeldingNasjonal,
        ferdigstillRegistrering: FerdigstillRegistrering,
        loggingMeta: LoggingMeta
    ): ResponseEntity<Any> {
        when (validationResult.status) {
            Status.OK,
            Status.MANUAL_PROCESSING -> {
                val veileder = oppgaveSecurityService.getNavIdent()

                if (ferdigstillRegistrering.oppgaveId != null) {
                    journalpostService.ferdigstillJournalpost(
                        accessToken = accessToken,
                        ferdigstillRegistrering = ferdigstillRegistrering,
                        receivedSykmelding = receivedSykmelding,
                        loggingMeta = loggingMeta,
                    )
                    oppgaveService.ferdigstillOppgave(
                        ferdigstillRegistrering,
                        null,
                        loggingMeta,
                        ferdigstillRegistrering.oppgaveId,
                    )
                }

                insertSykmeldingAndCreateJobs(receivedSykmelding, ferdigstillRegistrering, veileder)

                manuellOppgaveDAO
                    .ferdigstillSmRegistering(
                        sykmeldingId = ferdigstillRegistrering.sykmeldingId,
                        utfall = Utfall.OK,
                        ferdigstiltAv = veileder.veilederIdent,
                    )
                    .let {
                        return if (it > 0) {
                            ResponseEntity.noContent()
                        } else {
                            log.error(
                                "Ferdigstilling av manuelt registrert papirsykmelding feilet ved databaseoppdatering {}",
                                StructuredArguments.keyValue(
                                    "oppgaveId",
                                    ferdigstillRegistrering.oppgaveId,
                                ),
                            )
                            return ResponseEntity.internalServerError().body("Fant ingen uløst oppgave for oppgaveId ${ferdigstillRegistrering.oppgaveId}")


                        }
                    }
            }
            else -> {
                log.error(
                    "Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING",
                )
                return ResponseEntity.internalServerError()
            }
        }
    }

    private fun handleBrokenRule(
        validationResult: ValidationResult,
        oppgaveId: Int?,
    ): ResponseEntity<Any> {
        if (validationResult.status == Status.MANUAL_PROCESSING ){
            log.info("Ferdigstilling av papirsykmeldinger manuell registering traff regel MANUAL_PROCESSING {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),)
            return ResponseEntity.badRequest().body(validationResult)
        }
        if (validationResult.status == Status.OK){
            log.info("Ferdigstilling av papirsykmeldinger manuell registering traff regel MANUAL_PROCESSING {}",
                StructuredArguments.keyValue("oppgaveId", oppgaveId),)
            return ResponseEntity.badRequest().body(validationResult)
        }
        log.error("Ukjent status: ${validationResult.status} , papirsykmeldinger manuell registering kan kun ha ein av to typer statuser enten OK eller MANUAL_PROCESSING",)
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

    private suspend fun createReceivedSykmelding(sykmeldingId: String, oppgave: NasjonalManuellOppgaveDAO, loggingMeta: LoggingMeta, smRegistreringManuell: SmRegistreringManuell, callId: String, sykmelder: Sykmelder): ReceivedSykmeldingNasjonal {
        log.info("Henter pasient fra PDL {} ", loggingMeta)
        val pasient =
            personService.getPerson(
                id = smRegistreringManuell.pasientFnr,
                callId = callId,
            )

        val tssId = sykmelderService.getTssIdInfotrygd(sykmelder.fnr, "", loggingMeta, sykmeldingId)

        val datoOpprettet = oppgave.datoOpprettet
        val journalpostId = oppgave.journalpostId
        val fellesformat =
            mapsmRegistreringManuelltTilFellesformat(
                smRegistreringManuell = smRegistreringManuell,
                pdlPasient = pasient,
                sykmelder = sykmelder,
                sykmeldingId = sykmeldingId,
                datoOpprettet = datoOpprettet,
                journalpostId = journalpostId,
            )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding =
            healthInformation.toSykmelding(
                sykmeldingId,
                pasient.aktorId,
                sykmelder.aktorId,
                sykmeldingId,
                getLocalDateTime(msgHead.msgInfo.genDate)
            )

        return ReceivedSykmeldingNasjonal(
                sykmelding = sykmelding,
                personNrPasient = pasient.fnr,
                tlfPasient =
                    healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                personNrLege = sykmelder.fnr,
                navLogId = sykmeldingId,
                msgId = sykmeldingId,
                legekontorOrgNr = null,
                legekontorOrgName = "",
                legekontorHerId = null,
                legekontorReshId = null,
                mottattDato = oppgave.datoOpprettet ?: getLocalDateTime(msgHead.msgInfo.genDate),
                rulesetVersion = healthInformation.regelSettVersjon,
                fellesformat = fellesformatMarshaller.toString(fellesformat),
                tssid = tssId ?: "",
                merknader = createMerknad(sykmelding),
                partnerreferanse = null,
                legeHelsepersonellkategori =
                    sykmelder.godkjenninger?.getHelsepersonellKategori(),
                legeHprNr = sykmelder.hprNummer,
                vedlegg = null,
                utenlandskSykmelding = null,
            )

    }

    private fun createMerknad(sykmelding: Sykmelding): List<Merknad>? {
        val behandletTidspunkt = sykmelding.behandletTidspunkt.toLocalDate()
        val terskel = sykmelding.perioder.map { it.fom }.minOrNull()?.plusDays(7)
        return if (behandletTidspunkt != null && terskel != null && behandletTidspunkt > terskel) {
            listOf(Merknad("TILBAKEDATERT_PAPIRSYKMELDING", null))
        } else {
            null
        }
    }

    fun List<Godkjenning>.getHelsepersonellKategori(): String? =
        when {
            find { it.helsepersonellkategori?.verdi == "LE" } != null -> "LE"
            find { it.helsepersonellkategori?.verdi == "TL" } != null -> "TL"
            find { it.helsepersonellkategori?.verdi == "MT" } != null -> "MT"
            find { it.helsepersonellkategori?.verdi == "FT" } != null -> "FT"
            find { it.helsepersonellkategori?.verdi == "KI" } != null -> "KI"
            else -> {
                val verdi = firstOrNull()?.helsepersonellkategori?.verdi
                log.warn(
                    "Signerende behandler har ikke en helsepersonellkategori($verdi) vi kjenner igjen",
                )
                verdi
            }
        }

    fun mapToDao(
        papirManuellOppgave: PapirManuellOppgave,
        existingId: UUID?,
    ): NasjonalManuellOppgaveDAO {
        val mapper = jacksonObjectMapper()
        mapper.registerModules(JavaTimeModule())

        val nasjonalManuellOppgaveDAO =
            NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                fnr = papirManuellOppgave.fnr,
                aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet?.toLocalDateTime(),
                oppgaveId = papirManuellOppgave.oppgaveid,
                ferdigstilt = false,
                papirSmRegistrering =
                    PapirSmRegistering(
                        journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                        oppgaveId = papirManuellOppgave.papirSmRegistering.oppgaveId,
                        fnr = papirManuellOppgave.papirSmRegistering.fnr,
                        aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                        dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                        datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet,
                        sykmeldingId = papirManuellOppgave.papirSmRegistering.sykmeldingId,
                        syketilfelleStartDato = papirManuellOppgave.papirSmRegistering.syketilfelleStartDato,
                        arbeidsgiver = papirManuellOppgave.papirSmRegistering.arbeidsgiver,
                        medisinskVurdering = papirManuellOppgave.papirSmRegistering.medisinskVurdering,
                        skjermesForPasient = papirManuellOppgave.papirSmRegistering.skjermesForPasient,
                        perioder = papirManuellOppgave.papirSmRegistering.perioder,
                        prognose = papirManuellOppgave.papirSmRegistering.prognose,
                        utdypendeOpplysninger = papirManuellOppgave.papirSmRegistering.utdypendeOpplysninger,
                        tiltakNAV = papirManuellOppgave.papirSmRegistering.tiltakNAV,
                        tiltakArbeidsplassen = papirManuellOppgave.papirSmRegistering.tiltakArbeidsplassen,
                        andreTiltak = papirManuellOppgave.papirSmRegistering.andreTiltak,
                        meldingTilNAV = papirManuellOppgave.papirSmRegistering.meldingTilNAV,
                        meldingTilArbeidsgiver = papirManuellOppgave.papirSmRegistering.meldingTilArbeidsgiver,
                        kontaktMedPasient = papirManuellOppgave.papirSmRegistering.kontaktMedPasient,
                        behandletTidspunkt = papirManuellOppgave.papirSmRegistering.behandletTidspunkt,
                        behandler = papirManuellOppgave.papirSmRegistering.behandler,
                    ),
                utfall = null,
                ferdigstiltAv = null,
                datoFerdigstilt = null,
                avvisningsgrunn = null,
            )

        if (existingId != null) {
            nasjonalManuellOppgaveDAO.apply {
                id = existingId
            }
        }

        return nasjonalManuellOppgaveDAO
    }
}

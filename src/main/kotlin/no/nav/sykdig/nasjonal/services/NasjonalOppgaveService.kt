package no.nav.sykdig.nasjonal.services

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.gosys.GosysService
import no.nav.sykdig.shared.auditLogger.AuditLogger
import no.nav.sykdig.utenlandsk.api.getPdfResult
import no.nav.sykdig.gosys.OppgaveClient
import no.nav.sykdig.nasjonal.db.NasjonalOppgaveRepository
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.db.models.Utfall
import no.nav.sykdig.saf.SafClient
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.nasjonal.models.*
import no.nav.sykdig.shared.*
import no.nav.sykdig.shared.utils.getLoggingMeta
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val oppgaveClient: OppgaveClient,
    private val nasjonalCommonService: NasjonalCommonService,
    private val safClient: SafClient,
    private val nasjonalFerdigstillingsService: NasjonalFerdigstillingsService,
    private val metricRegister: MetricRegister,
    private val gosysService: GosysService,
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

        val eksisterendeOppgave = findBySykmeldingId(papirSmRegistering.sykmeldingId)
        if (eksisterendeOppgave != null) {
            logger.warn(
                "Papirsykmelding med sykmeldingId {} er allerede lagret i databasen. Ingen ny oppgave opprettes.",
                papirSmRegistering.sykmeldingId,
            )
            return
        }
        try {
            val oppgave = gosysService.opprettNasjonalOppgave(papirSmRegistering)
            lagreOppgave(papirSmRegistering.toPapirManuellOppgave(oppgave.id))
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

    fun lagreOppgave(
        papirManuellOppgave: PapirManuellOppgave,
        ferdigstilt: Boolean = false,
    ): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        securelog.info("Henter oppgave med sykmeldingId=${papirManuellOppgave.sykmeldingId}. Funnet: $eksisterendeOppgave")
        return if (eksisterendeOppgave != null) {
            log.info("Oppdaterer oppgave med sykmeldingId=${papirManuellOppgave.sykmeldingId}, database-id=${eksisterendeOppgave.id}")
            nasjonalOppgaveRepository.save(
                mapToDao(
                    papirManuellOppgave,
                    eksisterendeOppgave.id,
                    ferdigstilt,
                ),
            )
        } else {
            val nyOppgave = nasjonalOppgaveRepository.save(
                mapToDao(
                    papirManuellOppgave,
                    null,
                    ferdigstilt,
                ),
            )
            log.info("Lagret ny oppgave med sykmeldingId=${nyOppgave.sykmeldingId}, database-id=${nyOppgave.id}")
            securelog.info("Detaljer om lagret oppgave: $nyOppgave")

            nyOppgave
        }
    }

    fun oppdaterOppgave(sykmeldingId: String, utfall: String, ferdigstiltAv: String, avvisningsgrunn: String?, smRegistreringManuell: SmRegistreringManuell?): NasjonalManuellOppgaveDAO? {
        val existingOppgave = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId)

        if (existingOppgave == null) {
            log.info("Sykmelding $sykmeldingId not found")
            return null
        }

        val updatedOppgave = existingOppgave.copy(
            utfall = utfall,
            ferdigstiltAv = ferdigstiltAv,
            avvisningsgrunn = avvisningsgrunn,
            datoFerdigstilt = OffsetDateTime.now(),
            ferdigstilt = true,
            papirSmRegistrering = mapToUpdatedPapirSmRegistrering(existingOppgave, smRegistreringManuell),
        )

        securelog.info("Lagret oppgave med sykmeldingId ${updatedOppgave.sykmeldingId} og med database id ${updatedOppgave.id} som dette objektet: $updatedOppgave")
        return nasjonalOppgaveRepository.save(updatedOppgave)
    }


    private fun mapToUpdatedPapirSmRegistrering(existingOppgave: NasjonalManuellOppgaveDAO, smRegistreringManuell: SmRegistreringManuell?): PapirSmRegistering {
        val updatedPapirSmRegistrering = existingOppgave.papirSmRegistrering.copy(
            meldingTilArbeidsgiver = smRegistreringManuell?.meldingTilArbeidsgiver
                ?: existingOppgave.papirSmRegistrering.meldingTilArbeidsgiver,
            medisinskVurdering = smRegistreringManuell?.medisinskVurdering ?: existingOppgave.papirSmRegistrering.medisinskVurdering,
            meldingTilNAV = smRegistreringManuell?.meldingTilNAV ?: existingOppgave.papirSmRegistrering.meldingTilNAV,
            arbeidsgiver = smRegistreringManuell?.arbeidsgiver ?: existingOppgave.papirSmRegistrering.arbeidsgiver,
            kontaktMedPasient = smRegistreringManuell?.kontaktMedPasient ?: existingOppgave.papirSmRegistrering.kontaktMedPasient,
            perioder = smRegistreringManuell?.perioder ?: existingOppgave.papirSmRegistrering.perioder,
            behandletTidspunkt = smRegistreringManuell?.behandletDato ?: existingOppgave.papirSmRegistrering.behandletTidspunkt,
            syketilfelleStartDato = smRegistreringManuell?.syketilfelleStartDato ?: existingOppgave.papirSmRegistrering.syketilfelleStartDato,
            behandler = smRegistreringManuell?.behandler ?: existingOppgave.papirSmRegistrering.behandler,
            skjermesForPasient = smRegistreringManuell?.skjermesForPasient ?: existingOppgave.papirSmRegistrering.skjermesForPasient,
        )

        securelog.info("Updated papirSmRegistrering: $updatedPapirSmRegistrering to be saved in syk-dig-backend db nasjonal_manuellOppgave")
        return updatedPapirSmRegistrering
    }


    private fun findByOppgaveId(oppgaveId: String): NasjonalManuellOppgaveDAO? {
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId.toInt()) ?: return null
        return oppgave
    }

    fun findBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO? {
        val oppgave = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId)

        if (oppgave == null) {
            log.info("Fant ingen sykmelding med sykmeldingId $sykmeldingId")
            return null
        }
        log.info("papirsykmelding: henter sykmelding med id $sykmeldingId fra syk-dig-db")
        securelog.info("hentet nasjonalOppgave fra db $oppgave")
        return oppgave
    }

    fun getOppgave(oppgaveId: String): NasjonalManuellOppgaveDAO? {
        if(!isValidOppgaveId(oppgaveId)) {
            log.info("Invalid oppgaveId $oppgaveId")
            throw DgsEntityNotFoundException("Invalid oppgaveId does not contain only alphanumerical characters. oppgaveId: $oppgaveId")
        }

        val nasjonalOppgave = findByOppgaveId(oppgaveId)
        if (nasjonalOppgave != null) {
            log.info("papirsykmelding: henter oppgave med id $oppgaveId fra syk-dig-db")
            return nasjonalOppgave
        }
        log.info("Finner ikke uløst oppgave med id $oppgaveId")
        return null
    }

    suspend fun avvisOppgave(
        oppgaveId: String,
        request: String,
        navEnhet: String,
    ): ResponseEntity<HttpStatusCode> {
        val lokalOppgave = getOppgave(oppgaveId)
        if (lokalOppgave == null) {
            log.info("Fant ikke oppgave som skulle avvises: $oppgaveId")
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }
        if (lokalOppgave.ferdigstilt) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            return ResponseEntity(HttpStatus.NO_CONTENT)
        }

        val eksternOppgave = oppgaveClient.getNasjonalOppgave(oppgaveId, lokalOppgave.sykmeldingId)
        val avvisningsgrunn = mapper.readValue(request, AvvisSykmeldingRequest::class.java).reason

        log.info("Avviser oppgave med oppgaveId: $oppgaveId. Avvisningsgrunn: $avvisningsgrunn")
        val veilederIdent = nasjonalCommonService.getNavIdent().veilederIdent
        nasjonalFerdigstillingsService.ferdigstillNasjonalAvvistOppgave(lokalOppgave, eksternOppgave, navEnhet, avvisningsgrunn, veilederIdent)

        oppdaterOppgave(
            lokalOppgave.sykmeldingId,
            utfall = Utfall.AVVIST.toString(),
            ferdigstiltAv = veilederIdent,
            avvisningsgrunn = avvisningsgrunn,
            null,
        )
        auditLogger.info(
            AuditLogger()
                .createcCefMessage(
                    fnr = lokalOppgave.fnr,
                    operation = AuditLogger.Operation.WRITE,
                    requestPath = "/api/v1/oppgave/$oppgaveId/avvis",
                    permit = AuditLogger.Permit.PERMIT,
                    navEmail = nasjonalCommonService.getNavEmail(),
                ),
        )

        log.info("Har avvist oppgave med oppgaveId $oppgaveId")
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    fun mapToDao(
        papirManuellOppgave: PapirManuellOppgave,
        existingId: UUID?,
        ferdigstilt: Boolean = false,
    ): NasjonalManuellOppgaveDAO {
        mapper.registerModules(JavaTimeModule())
        securelog.info("Mapper til DAO: $papirManuellOppgave")

        val papirSmRegistering = papirManuellOppgave.papirSmRegistering

        val nasjonalManuellOppgaveDAO =
            NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = papirSmRegistering.journalpostId,
                fnr = papirManuellOppgave.fnr,
                aktorId = papirSmRegistering.aktorId,
                dokumentInfoId = papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirSmRegistering.datoOpprettet,
                oppgaveId = papirManuellOppgave.oppgaveid,
                ferdigstilt = ferdigstilt,
                papirSmRegistrering =
                    PapirSmRegistering(
                        journalpostId = papirSmRegistering.journalpostId,
                        oppgaveId = papirSmRegistering.oppgaveId,
                        fnr = papirSmRegistering.fnr,
                        aktorId = papirSmRegistering.aktorId,
                        dokumentInfoId = papirSmRegistering.dokumentInfoId,
                        datoOpprettet = papirSmRegistering.datoOpprettet,
                        sykmeldingId = papirSmRegistering.sykmeldingId,
                        syketilfelleStartDato = papirSmRegistering.syketilfelleStartDato,
                        arbeidsgiver = papirSmRegistering.arbeidsgiver,
                        medisinskVurdering = papirSmRegistering.medisinskVurdering,
                        skjermesForPasient = papirSmRegistering.skjermesForPasient,
                        perioder = papirSmRegistering.perioder,
                        prognose = papirSmRegistering.prognose,
                        utdypendeOpplysninger = papirSmRegistering.utdypendeOpplysninger,
                        tiltakNAV = papirSmRegistering.tiltakNAV,
                        tiltakArbeidsplassen = papirSmRegistering.tiltakArbeidsplassen,
                        andreTiltak = papirSmRegistering.andreTiltak,
                        meldingTilNAV = papirSmRegistering.meldingTilNAV,
                        meldingTilArbeidsgiver = papirSmRegistering.meldingTilArbeidsgiver,
                        kontaktMedPasient = papirSmRegistering.kontaktMedPasient,
                        behandletTidspunkt = papirSmRegistering.behandletTidspunkt,
                        behandler = papirSmRegistering.behandler,
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

    fun mapFromDao(
        nasjonalManuellOppgaveDAO: NasjonalManuellOppgaveDAO,
    ): PapirManuellOppgave {
        val papirSmRegistering = nasjonalManuellOppgaveDAO.papirSmRegistrering

        requireNotNull(nasjonalManuellOppgaveDAO.oppgaveId)
        requireNotNull(nasjonalManuellOppgaveDAO.dokumentInfoId)
        return PapirManuellOppgave(
            sykmeldingId = nasjonalManuellOppgaveDAO.sykmeldingId,
            fnr = nasjonalManuellOppgaveDAO.fnr,
            oppgaveid = nasjonalManuellOppgaveDAO.oppgaveId,
            papirSmRegistering = PapirSmRegistering(
                journalpostId = papirSmRegistering.journalpostId,
                oppgaveId = papirSmRegistering.oppgaveId,
                fnr = papirSmRegistering.fnr,
                aktorId = papirSmRegistering.aktorId,
                dokumentInfoId = papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirSmRegistering.datoOpprettet,
                sykmeldingId = papirSmRegistering.sykmeldingId,
                syketilfelleStartDato = papirSmRegistering.syketilfelleStartDato,
                arbeidsgiver = papirSmRegistering.arbeidsgiver,
                medisinskVurdering = papirSmRegistering.medisinskVurdering,
                skjermesForPasient = papirSmRegistering.skjermesForPasient,
                perioder = papirSmRegistering.perioder,
                prognose = papirSmRegistering.prognose,
                utdypendeOpplysninger = papirSmRegistering.utdypendeOpplysninger,
                tiltakNAV = papirSmRegistering.tiltakNAV,
                tiltakArbeidsplassen = papirSmRegistering.tiltakArbeidsplassen,
                andreTiltak = papirSmRegistering.andreTiltak,
                meldingTilNAV = papirSmRegistering.meldingTilNAV,
                meldingTilArbeidsgiver = papirSmRegistering.meldingTilArbeidsgiver,
                kontaktMedPasient = papirSmRegistering.kontaktMedPasient,
                behandletTidspunkt = papirSmRegistering.behandletTidspunkt,
                behandler = papirSmRegistering.behandler,
            ),
            pdfPapirSykmelding = byteArrayOf(),
            documents = listOf(Document(dokumentInfoId = nasjonalManuellOppgaveDAO.dokumentInfoId, tittel = "papirsykmelding")),
        )
    }

    fun getRegisterPdf(oppgaveId: String, dokumentInfoId: String): ResponseEntity<Any> {
        val oppgave = getOppgave(oppgaveId)
        requireNotNull(oppgave)
        val pdfResult = safClient.getPdfFraSaf(oppgave.journalpostId, dokumentInfoId, oppgaveId)
        return getPdfResult(pdfResult)
    }

    fun oppgaveTilGosys(oppgaveId: String) {
        val eksisterendeOppgave = getOppgave(oppgaveId) ?: return
        val navIdent = nasjonalCommonService.getNavIdent()
        val loggingMeta = getLoggingMeta(eksisterendeOppgave.sykmeldingId, eksisterendeOppgave)
        nasjonalFerdigstillingsService.ferdigstillOgSendOppgaveTilGosys(oppgaveId, eksisterendeOppgave)
        oppdaterOppgave(eksisterendeOppgave.sykmeldingId, Utfall.SENDT_TIL_GOSYS.toString(), navIdent.veilederIdent, null, null)

        log.info(
            "Ferdig å sende oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta),
        )
        metricRegister.sendtTilGosysNasjonal.increment()
    }

    fun deleteOppgave(sykmeldingId: String): Int {
        return nasjonalOppgaveRepository.deleteBySykmeldingId(sykmeldingId)
    }
}

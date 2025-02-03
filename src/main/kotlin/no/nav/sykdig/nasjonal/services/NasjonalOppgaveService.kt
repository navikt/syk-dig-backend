package no.nav.sykdig.nasjonal.services

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.auditLogger.AuditLogger
import no.nav.sykdig.shared.auditlog
import no.nav.sykdig.utenlandsk.api.getPdfResult
import no.nav.sykdig.shared.exceptions.NoOppgaveException
import no.nav.sykdig.gosys.OppgaveClient
import no.nav.sykdig.nasjonal.clients.SmregistreringClient
import no.nav.sykdig.nasjonal.db.NasjonalOppgaveRepository
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.nasjonal.db.models.Utfall
import no.nav.sykdig.saf.SafClient
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.nasjonal.models.*
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.shared.utils.getLoggingMeta
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val oppgaveClient: OppgaveClient,
    private val smregistreringClient: SmregistreringClient,
    private val nasjonalCommonService: NasjonalCommonService,
    private val safClient: SafClient,
    private val nasjonalFerdigstillingsService: NasjonalFerdigstillingsService,
    private val metricRegister: MetricRegister,
) {
    val log = applog()
    val securelog = securelog()
    val auditLogger = auditlog()
    val mapper = jacksonObjectMapper()

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

    fun lagreOppgaveMigrering(
        papirManuellOppgave: PapirManuellOppgave,
        ferdigstilt: Boolean,
        journalpostId: String?,
        aktorId: String?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        utfall: String?,
        ferdigstiltAv: String?,
        datoFerdigstilt: LocalDateTime?,
        avvisningsgrunn: String?,
    ): NasjonalManuellOppgaveDAO {

        log.info("lagrer oppgave med sykmeldingId=${papirManuellOppgave.sykmeldingId} fra $datoOpprettet")
        val nyOppgave = nasjonalOppgaveRepository.save(
            mapToDaoMigrering(
                papirManuellOppgave,
                ferdigstilt,
                journalpostId,
                aktorId,
                dokumentInfoId,
                datoOpprettet,
                utfall,
                ferdigstiltAv,
                datoFerdigstilt,
                avvisningsgrunn
            ),
        )
        return nyOppgave
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
            datoFerdigstilt = LocalDateTime.now(),
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
        if (!isValidOppgaveId(oppgaveId))
            throw IllegalArgumentException("Invalid oppgaveId does not contain only alphanumerical characters. oppgaveId: $oppgaveId")
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId.toInt()) ?: return null

        return oppgave
    }

    fun findBySykmeldingId(sykmeldingId: String): NasjonalManuellOppgaveDAO? {
        val oppgave = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId) ?: return null
        return oppgave
    }

    fun getNasjonalOppgave(oppgaveId: String): NasjonalManuellOppgaveDAO {
        val oppgave = findByOppgaveId(oppgaveId)
        if (oppgave == null) {
            log.warn("Fant ikke oppgave med id $oppgaveId Den kan kanskje være ferdigstilt fra før")
            throw NoOppgaveException("Fant ikke oppgave")
        }
        log.info("Hentet oppgave med id $oppgaveId")
        return oppgave
    }

    fun getOppgaveBySykmeldingId(sykmeldingId: String, authorization: String): NasjonalManuellOppgaveDAO? {
        val sykmelding = findBySykmeldingId(sykmeldingId)

        if (sykmelding != null) {
            log.info("papirsykmelding: henter sykmelding med id $sykmeldingId fra syk-dig-db")
            securelog.info("hentet nasjonalOppgave fra db $sykmelding")
            return sykmelding
        }
        log.info("papirsykmelding: henter ferdigstilt sykmelding med id $sykmeldingId gjennom syk-dig proxy")
        val ferdigstiltSykmeldingRequest = smregistreringClient.getFerdigstiltSykmeldingRequest(authorization, sykmeldingId)
        val papirManuellOppgave = ferdigstiltSykmeldingRequest.body
        if (papirManuellOppgave != null) {
            securelog.info("lagrer nasjonalOppgave i db $papirManuellOppgave")
            val lagretOppgave = lagreOppgave(papirManuellOppgave, ferdigstilt = true)
            return lagretOppgave
        }
        log.info(
            "Fant ingen ferdigstilte sykmeldinger med sykmeldingId $sykmeldingId",
        )
        return null
    }

    fun getOppgaveBySykmeldingIdSykDig(sykmeldingId: String, authorization: String): NasjonalManuellOppgaveDAO? {
        val sykmelding = findBySykmeldingId(sykmeldingId)
        if (sykmelding != null) {
            log.info("papirsykmelding: henter sykmelding med id $sykmeldingId fra syk-dig-db")
            securelog.info("hentet nasjonalOppgave fra db $sykmelding")
            return sykmelding
        }
        return null
    }

    fun getOppgave(oppgaveId: String, authorization: String): NasjonalManuellOppgaveDAO? {
        val nasjonalOppgave = findByOppgaveId(oppgaveId)
        if (nasjonalOppgave != null) {
            log.info("papirsykmelding: henter oppgave med id $oppgaveId fra syk-dig-db")
            return nasjonalOppgave
        }
        log.info("papirsykmelding: henter oppgave med id $oppgaveId gjennom syk-dig proxy")
        val oppgave = smregistreringClient.getOppgaveRequest(authorization, oppgaveId)
        log.info("har hentet papirManuellOppgave via syk-dig proxy")

        val papirManuellOppgave = oppgave.body
        if (papirManuellOppgave != null) {
            log.info("har hentet papirManuellOppgave via syk-dig proxy og oppgaven er ikke null")
            securelog.info("lagrer nasjonalOppgave i db $papirManuellOppgave")
            val lagretOppgave = lagreOppgave(papirManuellOppgave)
            return lagretOppgave
        }
        log.info("Finner ikke uløst oppgave med id $oppgaveId")
        return null
    }

    suspend fun avvisOppgave(
        oppgaveId: String,
        request: String,
        navEnhet: String,
        authorization: String,
    ): ResponseEntity<HttpStatusCode> {
        val lokalOppgave = getOppgave(oppgaveId, authorization)
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

        //TODO: papirsmregistrering gjøres non nullable etter migrering
        // grunn: det er ca 20 sykmeldinger fra 2020 med papirsmregistrering null
        // den skal aldri være null på tidspuntket her
        val papirSmRegistering = papirManuellOppgave.papirSmRegistering!!

        val nasjonalManuellOppgaveDAO =
            NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = papirSmRegistering.journalpostId,
                fnr = papirManuellOppgave.fnr,
                aktorId = papirSmRegistering.aktorId,
                dokumentInfoId = papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirSmRegistering.datoOpprettet?.toLocalDateTime(),
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

    fun mapToDaoMigrering(
        papirManuellOppgave: PapirManuellOppgave,
        ferdigstilt: Boolean,
        journalpostId: String?,
        aktorId: String?,
        dokumentInfoId: String?,
        datoOpprettet: LocalDateTime?,
        utfall: String?,
        ferdigstiltAv: String?,
        datoFerdigstilt: LocalDateTime?,
        avvisningsgrunn: String?,
    ): NasjonalManuellOppgaveDAO {
        mapper.registerModules(JavaTimeModule())
        securelog.info("Mapper til DAO: $papirManuellOppgave")

        val papirSmRegistering = papirManuellOppgave.papirSmRegistering

        // TODO: denne fjernes etter migrering - papirsmregistrering skal aldri være null
        // fordi det er sånn 20 sykmeldinger fra 2020 uten papirsmregistreringsobjekt i smreg db
        if (papirSmRegistering == null) {
            log.info("papirsmregistrering er null")
            val nasjonalManuellOppgaveDAO = NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = journalpostId!!,
                fnr = papirManuellOppgave.fnr,
                aktorId = aktorId,
                dokumentInfoId = dokumentInfoId,
                datoOpprettet = datoOpprettet,
                oppgaveId = papirManuellOppgave.oppgaveid,
                ferdigstilt = ferdigstilt,
                papirSmRegistrering = PapirSmRegistering(
                    journalpostId, papirManuellOppgave.oppgaveid.toString(), papirManuellOppgave.fnr, aktorId, dokumentInfoId, datoOpprettet!!.atOffset(ZoneOffset.ofHours(1)), papirManuellOppgave.sykmeldingId, null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null,
                ),
                utfall = utfall,
                ferdigstiltAv = ferdigstiltAv,
                datoFerdigstilt = datoFerdigstilt,
                avvisningsgrunn = avvisningsgrunn,
            )
            return nasjonalManuellOppgaveDAO
        } else {
            val nasjonalManuellOppgaveDAO =
                NasjonalManuellOppgaveDAO(
                    sykmeldingId = papirManuellOppgave.sykmeldingId,
                    journalpostId = papirSmRegistering.journalpostId,
                    fnr = papirManuellOppgave.fnr,
                    aktorId = papirSmRegistering.aktorId,
                    dokumentInfoId = papirSmRegistering.dokumentInfoId,
                    datoOpprettet = papirSmRegistering.datoOpprettet?.toLocalDateTime(),
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
                    utfall = utfall,
                    ferdigstiltAv = ferdigstiltAv,
                    datoFerdigstilt = datoFerdigstilt,
                    avvisningsgrunn = avvisningsgrunn,
                )
            return nasjonalManuellOppgaveDAO
        }
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

    fun getRegisterPdf(oppgaveId: String, authorization: String, dokumentInfoId: String): ResponseEntity<Any> {
        val oppgave = getOppgave(oppgaveId, authorization)
        requireNotNull(oppgave)
        val pdfResult = safClient.getPdfFraSaf(oppgave.journalpostId, dokumentInfoId, authorization)
        return getPdfResult(pdfResult)
    }

    fun oppgaveTilGosys(oppgaveId: String, authorization: String) {
        val eksisterendeOppgave = getOppgave(oppgaveId, authorization) ?: return
        val navIdent = nasjonalCommonService.getNavIdent()
        val loggingMeta = getLoggingMeta(eksisterendeOppgave.sykmeldingId, eksisterendeOppgave)
        nasjonalFerdigstillingsService.ferdigstillOgSendOppgaveTilGosys(oppgaveId, authorization, eksisterendeOppgave)
        oppdaterOppgave(eksisterendeOppgave.sykmeldingId, Utfall.SENDT_TIL_GOSYS.toString(), navIdent.veilederIdent, null, null)

        log.info(
            "Ferdig å sende oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta),
        )
        metricRegister.sendtTilGosysNasjonal.increment()
    }

    fun deleteOppgave(sykmeldingId: String): Boolean {
        val id = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId)?.id
        if (id != null) {
            nasjonalOppgaveRepository.deleteById(id)
        }
        return nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId) == null
    }

}

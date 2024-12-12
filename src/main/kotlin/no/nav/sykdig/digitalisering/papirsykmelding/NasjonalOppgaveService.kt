package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.api.getPdfResult
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.GosysService
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.mapAvvisningsgrunn
import no.nav.sykdig.digitalisering.papirsykmelding.api.SmregistreringClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.AvvisSykmeldingRequest
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Document
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.FerdigstillRegistrering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.Utfall
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.SafClient
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.metrics.MetricRegister
import no.nav.sykdig.securelog
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val oppgaveClient: OppgaveClient,
    private val personService: PersonService,
    private val ferdigstillingService: FerdigstillingService,
    private val smregistreringClient: SmregistreringClient,
    private val nasjonalCommonService: NasjonalCommonService,
    private val gosysService: GosysService,
    private val metricRegister: MetricRegister,
    private val safClient: SafClient,
) {
    val log = applog()
    val securelog = securelog()
    val mapper = jacksonObjectMapper()

    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave, ferdigstilt: Boolean = false): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        securelog.info("Forsøkte å hente eksisterende oppgave med sykmeldingId ${papirManuellOppgave.sykmeldingId} , fant følgende: $eksisterendeOppgave")

        if (eksisterendeOppgave != null) {
            log.info("Fant eksisterende oppgave med sykmeldingId ${papirManuellOppgave.sykmeldingId} , oppdaterer oppgave med database id ${eksisterendeOppgave.id}")
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.id, ferdigstilt))
        }
        val res = nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null, ferdigstilt))
        log.info("Lagret oppgave med sykmeldingId ${res.sykmeldingId} og med database id ${eksisterendeOppgave?.id}")
        securelog.info("Lagret oppgave med sykmeldingId ${res.sykmeldingId} og med database id ${eksisterendeOppgave?.id} og som dette objektet: $res")
        return res
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
            papirSmRegistrering = mapToUpdatedPapirSmRegistrering(existingOppgave, smRegistreringManuell)
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
        val oppgave = nasjonalOppgaveRepository.findBySykmeldingId(sykmeldingId)

        if (oppgave == null) return null
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

    suspend fun ferdigstillOppgave(
        ferdigstillRegistrering: FerdigstillRegistrering,
        beskrivelse: String?,
        loggingMeta: LoggingMeta,
        oppgaveId: String,
    ) {
        oppgaveClient.ferdigstillNasjonalOppgave(oppgaveId, ferdigstillRegistrering.sykmeldingId, ferdigstillRegistrering, loggingMeta)
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

    fun avvisOppgave(
        oppgaveId: String,
        request: String,
        navEnhet: String,
        authorization: String
    ): ResponseEntity<HttpStatusCode> {
        val eksisterendeOppgave = getOppgave(oppgaveId, authorization)

        if (eksisterendeOppgave == null) {
            log.info("Fant ikke oppgave som skulle avvises: $oppgaveId")
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }

        if (eksisterendeOppgave.ferdigstilt) {
            log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
            return ResponseEntity(HttpStatus.NO_CONTENT)
        }

        val avvisningsgrunn = mapper.readValue(request, AvvisSykmeldingRequest::class.java).reason
        val veilederIdent = nasjonalCommonService.getNavIdent().veilederIdent

        ferdigstillNasjonalAvvistOppgave(eksisterendeOppgave, navEnhet, avvisningsgrunn, veilederIdent)
        oppdaterOppgave(
            eksisterendeOppgave.sykmeldingId,
            utfall = Utfall.AVVIST.toString(),
            ferdigstiltAv = veilederIdent,
            avvisningsgrunn = avvisningsgrunn,
            null
        )

        log.info("Har avvist oppgave med oppgaveId $oppgaveId")
        return ResponseEntity(HttpStatus.NO_CONTENT)

    }


    fun mapToDao(
        papirManuellOppgave: PapirManuellOppgave,
        existingId: UUID?,
        ferdigstilt: Boolean = false
    ): NasjonalManuellOppgaveDAO {
        mapper.registerModules(JavaTimeModule())
        securelog.info("Mapper til DAO: $papirManuellOppgave")

        val nasjonalManuellOppgaveDAO =
            NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                fnr = papirManuellOppgave.fnr,
                aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet?.toLocalDateTime(),
                oppgaveId = papirManuellOppgave.oppgaveid,
                ferdigstilt = ferdigstilt,
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

    fun mapFromDao(
        nasjonalManuellOppgaveDAO: NasjonalManuellOppgaveDAO
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


    fun ferdigstillNasjonalAvvistOppgave(
        oppgave: NasjonalManuellOppgaveDAO,
        navEnhet: String,
        avvisningsgrunn: String?,
        veilederIdent: String,
    ) {

        if (oppgave.fnr != null) {
            val sykmeldt =
                personService.getPerson(
                    id = oppgave.fnr,
                    callId = oppgave.sykmeldingId,
                )
            val avvistGrunn = enumValues<Avvisingsgrunn>().find { it.name.equals(avvisningsgrunn, ignoreCase = true) }
            ferdigstillingService.ferdigstillNasjonalAvvistJournalpost(
                enhet = navEnhet,
                oppgave = oppgave,
                sykmeldt = sykmeldt,
                avvisningsGrunn = avvistGrunn?.let { mapAvvisningsgrunn(it, null) },
                loggingMeta = nasjonalCommonService.getLoggingMeta(oppgave.sykmeldingId, oppgave),
            )

        } else {
            log.error("Fant ikke fnr for oppgave med id $oppgave.oppgaveId")
        }
    }

    fun ferdigstillOgSendOppgaveTilGosys(oppgaveId: String, authorization: String) {
        val eksisterendeOppgave = getOppgave(oppgaveId, authorization)

        if (eksisterendeOppgave == null) {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw NoOppgaveException("Fant ikke oppgave med id $oppgaveId")
        }

        val sykmeldingId = eksisterendeOppgave.sykmeldingId

        val loggingMeta = nasjonalCommonService.getLoggingMeta(sykmeldingId, eksisterendeOppgave)

        log.info(
            "Sender nasjonal oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta)
        )

        val navIdent = nasjonalCommonService.getNavIdent().veilederIdent
        gosysService.sendOppgaveTilGosys(oppgaveId, sykmeldingId, navIdent)
        oppdaterOppgave(sykmeldingId, Utfall.SENDT_TIL_GOSYS.toString(), navIdent, null, null)

        log.info(
            "Ferdig å sende oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta)
        )

        metricRegister.sendtTilGosysNasjonal.increment()
    }

    fun getRegisterPdf(oppgaveId: String, authorization: String, dokumentInfoId: String): ResponseEntity<Any> {
        val oppgave = getOppgave(oppgaveId, authorization)
        requireNotNull(oppgave)
        val pdfResult = safClient.getPdfFraSaf(oppgave.journalpostId, dokumentInfoId, authorization)
        return getPdfResult(pdfResult)
    }
}

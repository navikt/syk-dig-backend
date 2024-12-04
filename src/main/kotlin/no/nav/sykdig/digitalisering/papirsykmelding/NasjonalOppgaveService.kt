package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.GosysService
import no.nav.sykdig.digitalisering.papirsykmelding.api.SmregistreringClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.Utfall
import no.nav.sykdig.digitalisering.tilgangskontroll.getNavEmail
import no.nav.sykdig.metrics.MetricRegister
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val gosysService: GosysService,
    private val metricRegister: MetricRegister,
    private val ferdigstillingService: FerdigstillingService,
) {
    val log = applog()

    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        if (eksisterendeOppgave.isPresent) {
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.get().id))
        }
        return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null))
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

    fun ferdigstillOgSendOppgaveTilGosys(oppgaveId: String) {
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId.toInt())

        if (!oppgave.isPresent) {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw NoOppgaveException("Fant ikke oppgave med id $oppgaveId")
        }

        val sykmeldingId = oppgave.get().sykmeldingId
        val journalpostId = oppgave.get().journalpostId
        val dokumentInfoId = oppgave.get().dokumentInfoId

        val loggingMeta =
            LoggingMeta(
                mottakId = sykmeldingId,
                dokumentInfoId = dokumentInfoId,
                msgId = sykmeldingId,
                sykmeldingId = sykmeldingId,
                journalpostId = journalpostId,
            )

        log.info(
            "Sender nasjonal oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta)
        )

        val navIdent = getNavEmail()
        gosysService.sendOppgaveTilGosys(oppgaveId, oppgave.get().sykmeldingId, navIdent)

        ferdigstillingService.ferdigstillOppgaveGosys(oppgaveId, Utfall.SENDT_TIL_GOSYS, navIdent)

        log.info(
            "Ferdig å sende oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta)
        )

        metricRegister.sendtTilGosysNasjonal.increment()
    }
}

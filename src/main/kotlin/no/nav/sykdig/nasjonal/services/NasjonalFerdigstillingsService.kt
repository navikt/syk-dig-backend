package no.nav.sykdig.nasjonal.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.services.GosysService
import no.nav.sykdig.oppgave.models.NasjonalOppgaveResponse
import no.nav.sykdig.oppgave.OppgaveClient
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.models.FerdigstillRegistrering
import no.nav.sykdig.nasjonal.models.Veileder
import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.utenlandsk.services.JournalpostService
import no.nav.sykdig.shared.securelog
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class NasjonalFerdigstillingsService(
    private val journalpostService: JournalpostService,
    private val nasjonalCommonService: NasjonalCommonService,
    private val sykmelderService: SykmelderService,
    private val oppgaveClient: OppgaveClient,
    private val gosysService: GosysService,
) {

    val log = applog()
    val securelog = securelog()


    suspend fun ferdigstillOppgave(
        ferdigstillRegistrering: FerdigstillRegistrering,
        beskrivelse: String?,
        loggingMeta: LoggingMeta,
        oppgaveId: String,
    ) {
        oppgaveClient.ferdigstillNasjonalOppgave(oppgaveId, ferdigstillRegistrering.sykmeldingId, ferdigstillRegistrering, loggingMeta, beskrivelse)
    }

    suspend fun ferdigstillNasjonalAvvistOppgave(
        lokalOppgave: NasjonalManuellOppgaveDAO,
        eksternOppgave: NasjonalOppgaveResponse,
        navEnhet: String,
        avvisningsgrunn: String?,
        veilederIdent: String,
    ) {
        val sykmeldingId = lokalOppgave.sykmeldingId
        val oppgaveId = lokalOppgave.oppgaveId
        val loggingMeta = nasjonalCommonService.getLoggingMeta(lokalOppgave.sykmeldingId, lokalOppgave)
        requireNotNull(lokalOppgave.oppgaveId)
        val sykmelder = sykmelderService.getSykmelderForAvvistOppgave(lokalOppgave.papirSmRegistrering.behandler?.hpr, lokalOppgave.sykmeldingId, lokalOppgave.oppgaveId)


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
        journalpostService.ferdigstillNasjonalJournalpost(ferdigstillRegistrering, lokalOppgave.papirSmRegistrering.perioder, loggingMeta)
        ferdigstillOppgave(
            ferdigstillRegistrering = ferdigstillRegistrering,
            beskrivelse = lagAvvisOppgavebeskrivelse(
                opprinneligBeskrivelse = eksternOppgave.beskrivelse,
                veileder = Veileder(veilederIdent),
                navEnhet = navEnhet,
                avvisSykmeldingReason = avvisningsgrunn,
            ),
            loggingMeta = loggingMeta,
            oppgaveId = oppgaveId.toString(),
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


    fun ferdigstillOgSendOppgaveTilGosys(oppgaveId: String, authorization: String, eksisterendeOppgave: NasjonalManuellOppgaveDAO) {
        val sykmeldingId = eksisterendeOppgave.sykmeldingId
        val loggingMeta = nasjonalCommonService.getLoggingMeta(sykmeldingId, eksisterendeOppgave)
        log.info(
            "Sender nasjonal oppgave med id $oppgaveId til Gosys {}",
            StructuredArguments.fields(loggingMeta),
        )
        val navIdent = nasjonalCommonService.getNavIdent().veilederIdent
        gosysService.sendNasjonalOppgaveTilGosys(oppgaveId, sykmeldingId, navIdent)
    }

}
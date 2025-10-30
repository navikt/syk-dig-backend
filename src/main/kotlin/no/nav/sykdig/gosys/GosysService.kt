package no.nav.sykdig.gosys

import java.time.DayOfWeek
import java.time.LocalDate
import no.nav.sykdig.gosys.models.NasjonalOppgaveResponse
import no.nav.sykdig.gosys.models.OppgaveStatus
import no.nav.sykdig.gosys.models.OpprettNasjonalOppgave
import no.nav.sykdig.nasjonal.models.PapirSmRegistering
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.metrics.MetricRegister
import org.springframework.stereotype.Component

@Component
class GosysService(
    private val oppgaveClient: OppgaveClient,
    private val metricRegister: MetricRegister,
) {
    val log = applog()

    fun sendOppgaveTilGosys(
        oppgaveId: String,
        sykmeldingId: String,
        veilederNavIdent: String,
        beskrivelse: String? = null,
        endretAvEnhetsnr: String,
    ) {
        val oppgave = oppgaveClient.getOppgave(oppgaveId, sykmeldingId)

        oppgaveClient.oppdaterGosysOppgave(
            oppgaveId,
            sykmeldingId,
            oppgave.versjon,
            oppgave.status,
            "FS22",
            veilederNavIdent,
            beskrivelse,
            endretAvEnhetsnr,
        )
    }

    fun sendNasjonalOppgaveTilGosys(
        oppgaveId: String,
        sykmeldingId: String,
        veilederNavIdent: String,
        beskrivelse: String? = null,
        endretAvEnhetsnr: String,
    ) {
        val oppgave = oppgaveClient.getNasjonalOppgave(oppgaveId, sykmeldingId)
        val oppdatertOppgave =
            oppgave.copy(
                behandlesAvApplikasjon = "FS22",
                tilordnetRessurs = veilederNavIdent,
                endretAvEnhetsnr = endretAvEnhetsnr,
            )
        oppgaveClient.oppdaterNasjonalGosysOppgave(
            oppdatertOppgave = oppdatertOppgave,
            sykmeldingId = sykmeldingId,
            oppgaveId = oppgaveId,
            veileder = veilederNavIdent,
        )
    }

    fun avvisOppgaveTilGosys(
        oppgaveId: String,
        sykmeldingId: String,
        veilederNavIdent: String,
        beskrivelse: String? = null,
        endretAvEnhetsnr: String?,
    ) {
        val oppgave = oppgaveClient.getOppgave(oppgaveId, sykmeldingId)

        oppgaveClient.oppdaterGosysOppgave(
            oppgaveId,
            sykmeldingId,
            oppgave.versjon,
            OppgaveStatus.FERDIGSTILT,
            "FS22",
            veilederNavIdent,
            beskrivelse,
            endretAvEnhetsnr,
        )
    }

    fun hentOppgave(oppgaveId: String, sykmeldingId: String) =
        oppgaveClient.getOppgave(oppgaveId, sykmeldingId)

    fun opprettNasjonalOppgave(papirSmRegistering: PapirSmRegistering): NasjonalOppgaveResponse {
        val oppgaveId = papirSmRegistering.oppgaveId?.toIntOrNull()
        if (oppgaveId == null) {
            val opprettOppgave = createOpprettOppgave(papirSmRegistering)
            val opprettetOppgave =
                oppgaveClient.opprettNasjonalOppgave(
                    opprettOppgave,
                    papirSmRegistering.sykmeldingId,
                )
            metricRegister.opprett_nasjonal_oppgave_counter.increment()
            log.info(
                "Opprettet manuell papirsykmeldingoppgave med oppgaveId: ${opprettetOppgave.id} og sykmeldingId: ${papirSmRegistering.sykmeldingId}"
            )
            return opprettetOppgave
        }
        val oppgave =
            oppgaveClient.getNasjonalOppgave(oppgaveId.toString(), papirSmRegistering.sykmeldingId)
        if (oppgave.status == "FERDIGSTILT") {
            log.warn(
                "Oppgave med id $oppgaveId er allerede ferdigstilt. Oppretter ny oppgave for sykmeldingId ${papirSmRegistering.sykmeldingId}"
            )
            val opprettOppgave = createOpprettOppgave(papirSmRegistering)
            val opprettetOppgave =
                oppgaveClient.opprettNasjonalOppgave(
                    opprettOppgave,
                    papirSmRegistering.sykmeldingId,
                )
            return opprettetOppgave
        }
        val patch =
            oppgave.copy(
                behandlesAvApplikasjon = "SMR",
                beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
                mappeId = null,
                aktivDato = LocalDate.now(),
                fristFerdigstillelse =
                    finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4)),
                prioritet = "HOY",
            )
        val oppdatertOppgave =
            oppgaveClient.oppdaterNasjonalGosysOppgave(
                oppdatertOppgave = patch,
                sykmeldingId = papirSmRegistering.sykmeldingId,
                oppgaveId = oppgaveId.toString(),
                veileder = null,
            )
        return oppdatertOppgave
    }

    private fun createOpprettOppgave(
        papirSmRegistering: PapirSmRegistering
    ): OpprettNasjonalOppgave {
        return OpprettNasjonalOppgave(
            aktoerId = papirSmRegistering.aktorId,
            opprettetAvEnhetsnr = "9999",
            behandlesAvApplikasjon = "SMR",
            beskrivelse = "Manuell registrering av sykmelding mottatt på papir",
            tema = "SYM",
            oppgavetype = "JFR",
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4)),
            prioritet = "HOY",
            journalpostId = papirSmRegistering.journalpostId,
        )
    }

    private fun finnFristForFerdigstillingAvOppgave(ferdistilleDato: LocalDate): LocalDate {
        return setToWorkDay(ferdistilleDato)
    }

    private fun setToWorkDay(ferdistilleDato: LocalDate): LocalDate =
        when (ferdistilleDato.dayOfWeek) {
            DayOfWeek.SATURDAY -> ferdistilleDato.plusDays(2)
            DayOfWeek.SUNDAY -> ferdistilleDato.plusDays(1)
            else -> ferdistilleDato
        }
}

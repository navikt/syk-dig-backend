package no.nav.sykdig.digitalisering

import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.digitalisering.ferdigstilling.GosysService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.regelvalidering.RegelvalideringService
import no.nav.sykdig.digitalisering.sykmelding.Sykmelding
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.generated.types.DigitaliseringsoppgaveStatus
import no.nav.sykdig.generated.types.DigitaliseringsoppgaveStatusEnum
import no.nav.sykdig.generated.types.OppdatertSykmeldingStatus
import no.nav.sykdig.generated.types.OppdatertSykmeldingStatusEnum
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.metrics.MetricRegister
import no.nav.sykdig.model.OppgaveDbModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class UtenlandskOppgaveService(
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val gosysService: GosysService,
    private val personService: PersonService,
    private val metricRegister: MetricRegister,
    private val regelvalideringService: RegelvalideringService,
) {
    private val log = applog()

    fun getDigitalisertSykmelding(sykmeldingId: String): SykDigOppgave {
        val oppgave = sykDigOppgaveService.getOppgaveFromSykmeldingId(sykmeldingId)
        return sykDigOppgave(oppgave)
    }

    fun getDigitaiseringsoppgave(oppgaveId: String): SykDigOppgave {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        return sykDigOppgave(oppgave)
    }

    private fun sykDigOppgave(
        oppgave: OppgaveDbModel,
    ): SykDigOppgave {
        val sykmeldt =
            personService.hentPerson(
                id = oppgave.fnr,
                callId = oppgave.sykmeldingId.toString(),
            )

        log.info("Hentet oppgave og sykmeldt for oppgave ${oppgave.oppgaveId} sykmeldingId ${oppgave.sykmeldingId}, lager SykDigOppgave!")

        return SykDigOppgave(oppgave, sykmeldt)
    }

    fun updateOppgave(
        oppgaveId: String,
        values: RegisterOppgaveValues,
        navEpost: String,
    ) {
        sykDigOppgaveService.updateOppgave(oppgaveId, values, navEpost)
    }

    fun ferdigstillOppgave(
        oppgaveId: String,
        navEpost: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String,
    ) {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val sykmeldt =
            personService.hentPerson(
                id = oppgave.fnr,
                callId = oppgave.sykmeldingId.toString(),
            )
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(sykmeldt, values)
        if (valideringsresultat.isNotEmpty()) {
            log.warn("Ferdigstilling av oppgave med id $oppgaveId feilet pga regelsjekk")
            throw ClientException(valideringsresultat.joinToString())
        }
        sykDigOppgaveService.ferdigstillOppgave(oppgave, navEpost, values, enhetId, sykmeldt)
        metricRegister.ferdigstiltOppgave.increment()
    }

    fun ferdigstillOppgaveSendTilGosys(
        oppgaveId: String,
        navIdent: String,
        navEpost: String,
    ): SykDigOppgave {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val sykmeldt =
            personService.hentPerson(
                id = oppgave.fnr,
                callId = oppgave.sykmeldingId.toString(),
            )

        gosysService.sendOppgaveTilGosys(oppgaveId, oppgave.sykmeldingId.toString(), navIdent)
        sykDigOppgaveService.ferdigstillOppgaveGosys(oppgave, navEpost)
        val updatedOppgave = sykDigOppgaveService.getOppgave(oppgaveId)

        metricRegister.sendtTilGosys.increment()
        return SykDigOppgave(updatedOppgave, sykmeldt)
    }

    @Transactional
    fun avvisOppgave(
        oppgaveId: String,
        navIdent: String,
        navEpost: String,
        enhetId: String,
        avvisningsgrunn: Avvisingsgrunn,
        avvisningsgrunnAnnet: String?,
    ): SykDigOppgave {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val sykmeldt =
            personService.hentPerson(
                id = oppgave.fnr,
                callId = oppgave.sykmeldingId.toString(),
            )

        val opprinneligBeskrivelse = gosysService.hentOppgave(oppgaveId, oppgave.sykmeldingId.toString()).beskrivelse

        sykDigOppgaveService.ferdigstillUtenlandskAvvistOppgave(oppgave, navEpost, enhetId, sykmeldt, avvisningsgrunn, avvisningsgrunnAnnet)

        val oppgaveBeskrivelse =
            lagOppgavebeskrivelse(
                avvisningsgrunn = mapAvvisningsgrunn(avvisningsgrunn, avvisningsgrunnAnnet),
                opprinneligBeskrivelse = opprinneligBeskrivelse,
                navIdent = navIdent,
            )

        gosysService.avvisOppgaveTilGosys(oppgaveId, oppgave.sykmeldingId.toString(), navIdent, oppgaveBeskrivelse)

        val updatedOppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        metricRegister.avvistSendtTilGosys.increment()
        return SykDigOppgave(updatedOppgave, sykmeldt)
    }

    fun lagOppgavebeskrivelse(
        avvisningsgrunn: String,
        opprinneligBeskrivelse: String?,
        navIdent: String,
        timestamp: LocalDateTime? = null,
    ): String {
        val oppdatertBeskrivelse = "Avvist utenlandsk sykmelding med årsak: $avvisningsgrunn"
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        val formattedTimestamp = (timestamp ?: LocalDateTime.now()).format(formatter)
        return "--- $formattedTimestamp $navIdent ---\n$oppdatertBeskrivelse\n\n$opprinneligBeskrivelse"
    }

    fun checkOppgaveState(oppgave: OppgaveDbModel): OppdatertSykmeldingStatusEnum {
        if(oppgave.ferdigstilt == null) {
            return OppdatertSykmeldingStatusEnum.IKKE_FERDIGSTILT
        }

        if(oppgave.tilbakeTilGosys) {
            return OppdatertSykmeldingStatusEnum.IKKE_EN_SYKMELDING
        }

        if(oppgave.avvisingsgrunn != null) {
            return OppdatertSykmeldingStatusEnum.AVVIST
        }

        return OppdatertSykmeldingStatusEnum.FERDIGSTILT
    }

    fun oppdaterDigitalisertSykmelding(sykmeldingId: String, enhetId: String, values: FerdistilltRegisterOppgaveValues, navEmail: String): OppdatertSykmeldingStatus {
        val oppgave = sykDigOppgaveService.getOppgaveFromSykmeldingId(sykmeldingId)

        val state = checkOppgaveState(oppgave)
        if(state != OppdatertSykmeldingStatusEnum.OPPDATERT) {
            return OppdatertSykmeldingStatus(
                sykmeldingId,
                state
            )
        }

        val sykmeldt =
            personService.hentPerson(
                id = oppgave.fnr,
                callId = oppgave.sykmeldingId.toString(),
            )
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(sykmeldt, values)
        if (valideringsresultat.isNotEmpty()) {
            log.warn("Oppdatering av sykmelding med id $sykmeldingId feilet pga regelsjekk")
            throw ClientException(valideringsresultat.joinToString())
        }
        sykDigOppgaveService.oppdaterSykmelding(oppgave, navEmail, values, enhetId, sykmeldt)
        metricRegister.oppdatertSykmeldingCounter.increment()
        return OppdatertSykmeldingStatus(
            sykmeldingId,
            OppdatertSykmeldingStatusEnum.OPPDATERT
        )
    }
}

fun mapAvvisningsgrunn(
    avvisningsgrunn: Avvisingsgrunn,
    avvisningsgrunnAnnet: String?,
): String {
    return when (avvisningsgrunn) {
        Avvisingsgrunn.MANGLENDE_DIAGNOSE -> "Det mangler diagnose"
        Avvisingsgrunn.MANGLENDE_PERIODE_ELLER_SLUTTDATO -> "Mangler periode eller sluttdato"
        Avvisingsgrunn.MANGLENDE_UNDERSKRIFT_ELLER_STEMPEL_FRA_SYKMELDER -> "Mangler underskrift eller stempel fra sykmelder"
        Avvisingsgrunn.MANGLENDE_ORGINAL_SYKMELDING -> "Mangler original sykmelding"
        Avvisingsgrunn.TILBAKEDATERT_SYKMELDING -> "Sykmeldingen er tilbakedatert"
        Avvisingsgrunn.RISIKOSAK -> "Risikosak"
        Avvisingsgrunn.FOR_LANG_PERIODE -> "Sykmeldingen har for lang periode"
        Avvisingsgrunn.BASERT_PAA_TELEFONKONTAKT -> "Sykmelding basert på telefonkontakt"
        Avvisingsgrunn.VARSLET_I_SAKEN -> "Varslet i saken - under vurdering"
        Avvisingsgrunn.MAXDATO_OPPNAADD -> "Maksdato er oppnådd"
        Avvisingsgrunn.LOPENDE_AAP -> "Løpende AAP"
        Avvisingsgrunn.DUPLIKAT -> "Sykmeldingen er et duplikat"
        Avvisingsgrunn.ANNET -> avvisningsgrunnAnnet ?: throw RuntimeException("Avvisningsgrunn Annet er null")
    }
}

fun getFristForFerdigstillingAvOppgave(today: LocalDate): LocalDate {
    return when (today.dayOfWeek) {
        DayOfWeek.FRIDAY -> today.plusDays(3)
        DayOfWeek.SATURDAY -> today.plusDays(2)
        else -> today.plusDays(1)
    }
}

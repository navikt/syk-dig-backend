package no.nav.sykdig.digitalisering

import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.digitalisering.ferdigstilling.GosysService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.regelvalidering.RegelvalideringService
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.logger
import no.nav.sykdig.metrics.MetricRegister
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class DigitaliseringsoppgaveService(
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val gosysService: GosysService,
    private val personService: PersonService,
    private val metricRegister: MetricRegister,
    private val regelvalideringService: RegelvalideringService,
) {

    private val log = logger()
    fun getDigitaiseringsoppgave(oppgaveId: String): SykDigOppgave {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )

        log.info("Hentet oppgave og sykmeldt for oppgave $oppgaveId, lager SykDigOppgave!")

        return SykDigOppgave(oppgave, sykmeldt)
    }

    fun updateOppgave(oppgaveId: String, values: RegisterOppgaveValues, navEpost: String) {
        sykDigOppgaveService.updateOppgave(oppgaveId, values, navEpost)
    }

    fun ferdigstillOppgave(
        oppgaveId: String,
        navEpost: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String,
    ) {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(sykmeldt, values)
        if (valideringsresultat.isNotEmpty()) {
            log.warn("Ferdigstilling av oppgave med id $oppgaveId feilet pga regelsjekk")
            throw ClientException(valideringsresultat.joinToString())
        }
        sykDigOppgaveService.ferdigstillOppgave(oppgave, navEpost, values, enhetId, sykmeldt)
        metricRegister.FERDIGSTILT_OPPGAVE.increment()
    }

    fun ferdigstillOppgaveSendTilGosys(
        oppgaveId: String,
        navIdent: String,
        navEpost: String,
    ): SykDigOppgave {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )

        gosysService.sendOppgaveTilGosys(oppgaveId, oppgave.sykmeldingId.toString(), navIdent)
        sykDigOppgaveService.ferdigstillOppgaveGosys(oppgave, navEpost)
        val updatedOppgave = sykDigOppgaveService.getOppgave(oppgaveId)

        metricRegister.SENDT_TIL_GOSYS.increment()
        return SykDigOppgave(updatedOppgave, sykmeldt)
    }

    @Transactional
    fun avvisOppgave(
        oppgaveId: String,
        navIdent: String,
        navEpost: String,
        enhetId: String,
        avvisningsgrunn: Avvisingsgrunn,
    ): SykDigOppgave {
        val oppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        val sykmeldt = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )

        val opprinneligBeskrivelse = gosysService.hentOppgave(oppgaveId, oppgave.sykmeldingId.toString()).beskrivelse

        sykDigOppgaveService.ferdigstillAvvistOppgave(oppgave, navEpost, enhetId, sykmeldt, avvisningsgrunn)

        val oppgaveBeskrivelse = lagOppgavebeskrivelse(
            avvisningsgrunn = mapAvvisningsgrunn(avvisningsgrunn),
            opprinneligBeskrivelse = opprinneligBeskrivelse,
            navIdent = navIdent,
        )

        gosysService.avvisOppgaveTilGosys(oppgaveId, oppgave.sykmeldingId.toString(), navIdent, oppgaveBeskrivelse)

        val updatedOppgave = sykDigOppgaveService.getOppgave(oppgaveId)
        metricRegister.AVVIST_SENDT_TIL_GOSYS.increment()
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
}

fun mapAvvisningsgrunn(avvisningsgrunn: Avvisingsgrunn): String {
    return when (avvisningsgrunn) {
        Avvisingsgrunn.MANGLENDE_DIAGNOSE -> "Det mangler diagnose"
        Avvisingsgrunn.MANGLENDE_PERIODE_ELLER_SLUTTDATO -> "Mangler periode eller sluttdato"
        Avvisingsgrunn.MANGLENDE_UNDERSKRIFT_ELLER_STEMPEL_FRA_SYKMELDER -> "Mangler underskrift eller stempel fra sykmelder"
        Avvisingsgrunn.MANGLENDE_ORGINAL_SYKMELDING -> "Mangler original sykmelding"
    }
}

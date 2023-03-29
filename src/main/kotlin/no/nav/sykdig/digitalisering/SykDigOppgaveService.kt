package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.db.toSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.logger
import no.nav.sykdig.model.OppgaveDbModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SykDigOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val ferdigstillingService: FerdigstillingService,
) {
    private val log = logger()

    fun getOppgave(oppgaveId: String): OppgaveDbModel {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave == null) {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }
        log.info("Hentet oppgave med id $oppgaveId")
        return oppgave
    }

    fun updateOppgave(oppgaveId: String, registerOppgaveValues: RegisterOppgaveValues, navEpost: String) {
        val oppgave = getOppgave(oppgaveId)
        val sykmelding = toSykmelding(oppgave, registerOppgaveValues)
        oppgaveRepository.updateOppgave(oppgave, sykmelding, navEpost, false)
    }

    fun ferdigstillOppgaveGosys(
        oppgave: OppgaveDbModel,
        navEpost: String,
    ) {
        val sykmelding = oppgaveRepository.getLastSykmelding(oppgave.oppgaveId)
        oppgaveRepository.ferdigstillOppgaveGosys(oppgave, navEpost, sykmelding)
    }

    fun ferdigstillAvvistOppgave(
        oppgave: OppgaveDbModel,
        navEpost: String,
        enhetId: String,
        sykmeldt: Person,
        avvisningsgrunn: Avvisingsgrunn,
    ) {
        val sykmelding = oppgaveRepository.getLastSykmelding(oppgave.oppgaveId)
        oppgaveRepository.ferdigstillAvvistOppgave(oppgave, navEpost, sykmelding, avvisningsgrunn)
        ferdigstillingService.ferdigstillAvvistJournalpost(
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
        )
    }

    @Transactional
    fun ferdigstillOppgave(
        oppgave: OppgaveDbModel,
        navEpost: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String,
        sykmeldt: Person,
    ) {
        val sykmelding = toSykmelding(oppgave, values)

        oppgaveRepository.updateOppgave(oppgave, sykmelding, navEpost, true)
        ferdigstillingService.ferdigstill(
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            validatedValues = values,
        )
    }
}

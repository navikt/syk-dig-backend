package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.db.toSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.logger
import no.nav.sykdig.model.OppgaveDbModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OppgaveService(
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

    fun updateOppgave(oppgaveId: String, registerOppgaveValues: RegisterOppgaveValues, ident: String) {
        val oppgave = getOppgave(oppgaveId)
        val sykmelding = toSykmelding(oppgave, registerOppgaveValues)
        oppgaveRepository.updateOppgave(oppgave, sykmelding, ident, false)
    }

    fun ferdigstillOppgaveGosys(
        oppgave: OppgaveDbModel,
        ident: String,
    ) {
        val sykmelding = oppgaveRepository.getLastSykmelding(oppgave.oppgaveId)
        oppgaveRepository.ferdigstillOppgaveGosys(oppgave, ident, sykmelding)
    }

    @Transactional
    fun ferdigstillOppgave(
        oppgave: OppgaveDbModel,
        ident: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String,
        sykmeldt: Person,
    ) {
        val sykmelding = toSykmelding(oppgave, values)

        oppgaveRepository.updateOppgave(oppgave, sykmelding, ident, true)
        ferdigstillingService.ferdigstill(
            navnSykmelder = values.skrevetLand, // TODO: finn ut hvor man skal få dette navnet fra
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            validatedValues = values,
        )
    }
}

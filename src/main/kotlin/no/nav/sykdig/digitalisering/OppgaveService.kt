package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.logger
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val ferdigstillingService: FerdigstillingService,
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
) {
    private val log = logger()

    fun getOppgave(oppgaveId: String): DigitaliseringsoppgaveDbModel {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave == null) {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }

        if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(oppgave.fnr)) {
            log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
            throw IkkeTilgangException("Innlogget bruker har ikke tilgang")
        }

        log.info("Hentet oppgave med id $oppgaveId")
        return oppgave
    }

    fun updateOppgave(oppgaveId: String, values: SykmeldingUnderArbeidValues, ident: String) {
        oppgaveRepository.updateOppgave(oppgaveId, values, ident, false)
    }

    @Transactional
    fun ferigstillOppgave(
        oppgaveId: String,
        ident: String,
        values: SykmeldingUnderArbeidValues,
        validatedValues: ValidatedOppgaveValues,
        enhetId: String,
        person: Person,
        oppgave: DigitaliseringsoppgaveDbModel,
    ) {
        requireNotNull(oppgave.dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }

        ferdigstillingService.ferdigstill(
            oppgaveId = oppgaveId,
            navnSykmelder = person.navn.toFormattedNameString(),
            land = validatedValues.skrevetLand,
            fnr = person.fnr,
            enhet = enhetId,
            dokumentinfoId = oppgave.dokumentInfoId,
            journalpostId = oppgave.journalpostId,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )
        oppgaveRepository.updateOppgave(oppgaveId, values, ident, true)
    }
}

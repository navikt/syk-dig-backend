package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.utils.toSykmelding
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.logger
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.utils.extractHelseOpplysningerArbeidsuforhet
import no.nav.sykdig.utils.fellesformatMarshaller
import no.nav.sykdig.utils.get
import no.nav.sykdig.utils.mapToFellesformat
import no.nav.sykdig.utils.toString
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
            receivedSykmelding = mapToReceivedSykmelding(
                validatedValues,
                oppgave,
                person,
                values.harAndreRelevanteOpplysninger
            )
        )
        oppgaveRepository.updateOppgave(oppgaveId, values, ident, true)
    }

    fun mapToReceivedSykmelding(
        validatedValues: ValidatedOppgaveValues,
        oppgave: DigitaliseringsoppgaveDbModel,
        person: Person,
        harAndreRelevanteOpplysninger: Boolean?
    ): ReceivedSykmelding {

        val fellesformat = mapToFellesformat(
            oppgave = oppgave,
            validatedValues = validatedValues,
            person = person,
            sykmeldingId = oppgave.sykmeldingId.toString(),
            datoOpprettet = oppgave.opprettet.toLocalDateTime(),
            journalpostId = oppgave.journalpostId
        )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = oppgave.sykmeldingId.toString(),
            pasientAktoerId = "",
            legeAktoerId = "",
            msgId = oppgave.sykmeldingId.toString(),
            signaturDato = msgHead.msgInfo.genDate
        )

        return ReceivedSykmelding(
            sykmelding = sykmelding,
            personNrPasient = person.fnr,
            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
            personNrLege = "",
            navLogId = oppgave.sykmeldingId.toString(),
            msgId = oppgave.sykmeldingId.toString(),
            legekontorOrgNr = null,
            legekontorOrgName = "",
            legekontorHerId = null,
            legekontorReshId = null,
            mottattDato = oppgave.opprettet.toLocalDateTime(),
            rulesetVersion = healthInformation.regelSettVersjon,
            fellesformat = fellesformatMarshaller.toString(fellesformat),
            tssid = null,
            merknader = null,
            partnerreferanse = null,
            legeHelsepersonellkategori = null,
            legeHprNr = null,
            vedlegg = null,
            utenlandskSykmelding = UtenlandskSykmelding(
                validatedValues.skrevetLand,
                harAndreRelevanteOpplysninger ?: false
            )
        )

    }
}

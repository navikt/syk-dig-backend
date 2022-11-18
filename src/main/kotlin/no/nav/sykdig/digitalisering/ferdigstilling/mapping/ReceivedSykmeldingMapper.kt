package no.nav.sykdig.digitalisering.ferdigstilling.mapping

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import java.time.LocalDateTime

fun mapToReceivedSykmelding(
    ferdigstillteRegisterOppgaveValues: FerdistilltRegisterOppgaveValues,
    sykmeldt: Person,
    harAndreRelevanteOpplysninger: Boolean?,
    sykmeldingId: String,
    journalpostId: String,
    opprettet: LocalDateTime
): ReceivedSykmelding {

    val fellesformat = mapToFellesformat(
        validatedValues = ferdigstillteRegisterOppgaveValues,
        person = sykmeldt,
        sykmeldingId = sykmeldingId,
        datoOpprettet = opprettet,
        journalpostId = journalpostId
    )

    val sykmelding = extractHelseOpplysningerArbeidsuforhet(fellesformat).toSykmelding(
        sykmeldingId = sykmeldingId,
        pasientAktoerId = "", // TODO: Hva skal vi gjøre her?
        msgId = sykmeldingId,
        signaturDato = opprettet
    )

    return ReceivedSykmelding(
        sykmelding = sykmelding,
        personNrPasient = sykmeldt.fnr,
        tlfPasient = null,
        personNrLege = "", // TODO: Hva skal vi gjøre med dette?
        navLogId = sykmeldingId,
        msgId = sykmeldingId,
        legekontorOrgNr = null,
        legekontorOrgName = "", // TODO: hva skal dette være?
        legekontorHerId = null,
        legekontorReshId = null,
        mottattDato = opprettet,
        rulesetVersion = null,
        fellesformat = fellesformatMarshaller.toString(fellesformat),
        tssid = null,
        merknader = null,
        partnerreferanse = null,
        legeHelsepersonellkategori = null,
        legeHprNr = null,
        vedlegg = null,
        utenlandskSykmelding = UtenlandskSykmelding(
            ferdigstillteRegisterOppgaveValues.skrevetLand,
            harAndreRelevanteOpplysninger ?: false
        )
    )
}

package no.nav.sykdig.digitalisering.ferdigstilling.mapping

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import java.time.LocalDateTime

fun mapToReceivedSykmelding(
    ferdigstillteRegisterOppgaveValues: FerdistilltRegisterOppgaveValues,
    sykmeldt: Person,
    sykmeldingId: String,
    journalpostId: String,
    opprettet: LocalDateTime,
): ReceivedSykmelding {
    val fellesformat = mapToFellesformat(
        validatedValues = ferdigstillteRegisterOppgaveValues,
        person = sykmeldt,
        sykmeldingId = sykmeldingId,
        datoOpprettet = opprettet,
        journalpostId = journalpostId,
    )

    val sykmelding = extractHelseOpplysningerArbeidsuforhet(fellesformat).toSykmelding(
        sykmeldingId = sykmeldingId,
        pasientAktoerId = sykmeldt.aktorId,
        msgId = sykmeldingId,
        signaturDato = opprettet,
    )

    return ReceivedSykmelding(
        sykmelding = sykmelding,
        personNrPasient = sykmeldt.fnr,
        tlfPasient = null,
        personNrLege = "", // Denne skal være blank siden vi ikke har fnr for lege, men feltet er påkrevd i formatet
        navLogId = sykmeldingId,
        msgId = sykmeldingId,
        legekontorOrgNr = null,
        legekontorOrgName = "", // Denne skal være blank siden vi ikkje har noe org name på legekontoret, men feltet er påkrevd i formatet
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
            ferdigstillteRegisterOppgaveValues.folkeRegistertAdresseErBrakkeEllerTilsvarende ?: false,
        ),
    )
}

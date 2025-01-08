package no.nav.sykdig.utenlandsk.mapping

import no.nav.sykdig.utenlandsk.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.pdl.Person
import no.nav.sykdig.utenlandsk.model.ReceivedSykmelding
import no.nav.sykdig.felles.Status
import no.nav.sykdig.utenlandsk.model.UtenlandskSykmelding
import no.nav.sykdig.felles.ValidationResult
import java.time.LocalDateTime

fun mapToReceivedSykmelding(
    ferdigstillteRegisterOppgaveValues: FerdistilltRegisterOppgaveValues,
    sykmeldt: Person,
    sykmeldingId: String,
    journalpostId: String,
    opprettet: LocalDateTime,
): ReceivedSykmelding {
    val fellesformat =
        mapToFellesformat(
            validatedValues = ferdigstillteRegisterOppgaveValues,
            person = sykmeldt,
            sykmeldingId = sykmeldingId,
            datoOpprettet = opprettet,
            journalpostId = journalpostId,
        )

    val sykmelding =
        extractHelseOpplysningerArbeidsuforhet(fellesformat).toSykmelding(
            sykmeldingId = sykmeldingId,
            pasientAktoerId = sykmeldt.aktorId,
            msgId = sykmeldingId,
            signaturDato = opprettet,
        )

    return ReceivedSykmelding(
        sykmelding = sykmelding,
        personNrPasient = sykmeldt.fnr,
        tlfPasient = null,
        // Denne skal være blank siden vi ikke har fnr for lege, men feltet er påkrevd i formatet
        personNrLege = "",
        navLogId = sykmeldingId,
        msgId = sykmeldingId,
        legekontorOrgNr = null,
        // Denne skal være blank siden vi ikkje har noe org name på legekontoret, men feltet er påkrevd i formatet
        legekontorOrgName = "",
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
        utenlandskSykmelding =
            UtenlandskSykmelding(
                ferdigstillteRegisterOppgaveValues.skrevetLand,
                ferdigstillteRegisterOppgaveValues.folkeRegistertAdresseErBrakkeEllerTilsvarende ?: false,
                erAdresseUtland = ferdigstillteRegisterOppgaveValues.erAdresseUtland ?: false,
            ),
        validationResult =
            ValidationResult(
                status = Status.OK,
                ruleHits = emptyList(),
            ),
    )
}

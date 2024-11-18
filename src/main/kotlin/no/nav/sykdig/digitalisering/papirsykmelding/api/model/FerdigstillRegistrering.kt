package no.nav.sykdig.digitalisering.papirsykmelding.api.model

import no.nav.sykdig.oppgavemottak.Oppgave


data class FerdigstillRegistrering(
    val oppgaveId: Int?,
    val journalpostId: String,
    val dokumentInfoId: String?,
    val pasientFnr: String,
    val sykmeldingId: String,
    val sykmelder: Sykmelder,
    val navEnhet: String,
    val veileder: Veileder,
    val avvist: Boolean,
    val oppgave: Oppgave?,
)

class Veileder(
    val veilederIdent: String,
)


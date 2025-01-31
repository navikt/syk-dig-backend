package no.nav.sykdig.nasjonal.models

import no.nav.sykdig.gosys.models.NasjonalOppgaveResponse


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
    val oppgave: NasjonalOppgaveResponse?,
)

class Veileder(
    val veilederIdent: String,
)


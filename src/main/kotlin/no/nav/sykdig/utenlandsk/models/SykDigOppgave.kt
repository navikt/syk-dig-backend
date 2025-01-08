package no.nav.sykdig.utenlandsk.models

import no.nav.sykdig.pdl.Person

data class SykDigOppgave(
    val oppgaveDbModel: OppgaveDbModel,
    val person: Person,
)

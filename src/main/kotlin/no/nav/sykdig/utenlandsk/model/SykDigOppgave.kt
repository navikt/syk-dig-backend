package no.nav.sykdig.utenlandsk.model

import no.nav.sykdig.pdl.Person

data class SykDigOppgave(
    val oppgaveDbModel: OppgaveDbModel,
    val person: Person,
)

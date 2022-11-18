package no.nav.sykdig.digitalisering

import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.model.OppgaveDbModel

data class SykDigOppgave(
    val oppgaveDbModel: OppgaveDbModel,
    val person: Person,
)

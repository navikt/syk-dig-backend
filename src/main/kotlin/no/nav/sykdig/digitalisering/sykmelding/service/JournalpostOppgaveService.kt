package no.nav.sykdig.digitalisering.sykmelding.service

import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.*

@Service
class JournalpostOppgaveService(
    private val oppgaveClient: OppgaveClient,
    private val oppgaveSecurityService: OppgaveSecurityService,
    private val oppgaveRepository: OppgaveRepository,
) {
    fun opprettOgLagreOppgave(journalpost: SafQueryJournalpost, journalpostId: String, fnr: String) {
        val response = oppgaveClient.opprettOppgave(
            journalpostId = journalpostId,
            tema = journalpost.journalpost?.tema!!,
            oppgavetype = "BEH_SED",
            prioritet = "NORM",
            aktivDato = OffsetDateTime.now(),
            behandlesAvApplikasjon = "FS22",
        )

        val dokumenter = journalpost.journalpost.dokumenter.map {
            DokumentDbModel(it.dokumentInfoId, it.tittel ?: "Mangler Tittel")
        }
        val oppgave = OppgaveDbModel(
            oppgaveId = response.id.toString(),
            fnr = fnr,
            journalpostId = journalpostId,
            dokumentInfoId = journalpost.journalpost.dokumenter.first().dokumentInfoId,
            dokumenter = dokumenter,
            opprettet = response.aktivDato,
            ferdigstilt = null,
            tilbakeTilGosys = true,
            avvisingsgrunn = null,
            sykmeldingId = UUID.randomUUID(),
            type = "UTLAND",
            sykmelding = null,
            endretAv = oppgaveSecurityService.getSaksbehandlerId(),
            timestamp = OffsetDateTime.now(),
            source = "syk-dig",
        )
        oppgaveRepository.lagreOppgave(oppgave)
    }
}

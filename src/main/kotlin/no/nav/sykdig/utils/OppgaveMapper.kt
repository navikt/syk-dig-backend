package no.nav.sykdig.utils

import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.oppgavemottak.DigitaliseringsoppgaveKafka
import java.time.OffsetDateTime
import java.util.UUID

fun toOppgaveDbModel(
    digitaliseringsoppgave: DigitaliseringsoppgaveKafka,
    opprettet: OffsetDateTime,
    sykmeldingId: String,
) = OppgaveDbModel(
    oppgaveId = digitaliseringsoppgave.oppgaveId,
    fnr = digitaliseringsoppgave.fnr,
    journalpostId = digitaliseringsoppgave.journalpostId,
    dokumentInfoId = digitaliseringsoppgave.dokumentInfoId,
    dokumenter = digitaliseringsoppgave.dokumenter?.map {
        DokumentDbModel(
            dokumentInfoId = it.dokumentInfoId,
            tittel = it.tittel,
        )
    },
    opprettet = opprettet,
    ferdigstilt = null,
    tilbakeTilGosys = false,
    avvisingsgrunn = null,
    sykmeldingId = UUID.fromString(sykmeldingId),
    type = digitaliseringsoppgave.type,
    sykmelding = null,
    endretAv = "syk-dig-backend",
    timestamp = opprettet,
    source = digitaliseringsoppgave.source,
)

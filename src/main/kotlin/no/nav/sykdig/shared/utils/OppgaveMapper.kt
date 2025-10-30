package no.nav.sykdig.shared.utils

import java.time.OffsetDateTime
import java.util.UUID
import no.nav.sykdig.utenlandsk.kafka.DigitaliseringsoppgaveScanning
import no.nav.sykdig.utenlandsk.models.DokumentDbModel
import no.nav.sykdig.utenlandsk.models.OppgaveDbModel

fun toOppgaveDbModel(
    digitaliseringsoppgave: DigitaliseringsoppgaveScanning,
    opprettet: OffsetDateTime,
    sykmeldingId: String,
) =
    OppgaveDbModel(
        oppgaveId = digitaliseringsoppgave.oppgaveId,
        fnr = digitaliseringsoppgave.fnr,
        journalpostId = digitaliseringsoppgave.journalpostId,
        dokumentInfoId = digitaliseringsoppgave.dokumentInfoId,
        dokumenter =
            digitaliseringsoppgave.dokumenter.map {
                DokumentDbModel(dokumentInfoId = it.dokumentInfoId, tittel = it.tittel)
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

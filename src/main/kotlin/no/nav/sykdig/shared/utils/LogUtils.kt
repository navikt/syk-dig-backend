package no.nav.sykdig.shared.utils

import no.nav.sykdig.nasjonal.db.models.NasjonalManuellOppgaveDAO
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.utenlandsk.models.OppgaveDbModel

fun getLoggingMeta(sykmeldingId: String, oppgave: Any?): LoggingMeta {
    return when (oppgave) {
        is OppgaveDbModel -> LoggingMeta(
            mottakId = sykmeldingId,
            dokumentInfoId = oppgave.dokumentInfoId,
            msgId = sykmeldingId,
            sykmeldingId = sykmeldingId,
            journalpostId = oppgave.journalpostId
        )
        is NasjonalManuellOppgaveDAO -> LoggingMeta(
            mottakId = sykmeldingId,
            dokumentInfoId = oppgave.dokumentInfoId,
            msgId = sykmeldingId,
            sykmeldingId = sykmeldingId,
            journalpostId = oppgave.journalpostId
        )
        else -> LoggingMeta(
            mottakId = sykmeldingId,
            dokumentInfoId = null,
            msgId = sykmeldingId,
            sykmeldingId = sykmeldingId,
            journalpostId = null
        )
    }
}

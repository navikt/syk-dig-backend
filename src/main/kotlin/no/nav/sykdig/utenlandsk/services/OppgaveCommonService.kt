package no.nav.sykdig.utenlandsk.services

import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.utenlandsk.models.OppgaveDbModel
import org.springframework.stereotype.Component

@Component
class OppgaveCommonService {

    fun getLoggingMeta(sykmeldingId: String, oppgave: OppgaveDbModel?): LoggingMeta {
        return LoggingMeta(
            mottakId = sykmeldingId,
            dokumentInfoId = oppgave?.dokumentInfoId,
            msgId = sykmeldingId,
            sykmeldingId = sykmeldingId,
            journalpostId = oppgave?.journalpostId,
        )
    }
}
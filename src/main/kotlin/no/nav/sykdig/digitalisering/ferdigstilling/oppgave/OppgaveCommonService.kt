package no.nav.sykdig.digitalisering.ferdigstilling.oppgave

import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.model.OppgaveDbModel
import org.springframework.stereotype.Component

@Component
class OppgaveCommonService {

    fun getLoggingMeta(sykmeldingId: String, oppgave:OppgaveDbModel?): LoggingMeta {
        return LoggingMeta(
            mottakId = sykmeldingId,
            dokumentInfoId = oppgave?.dokumentInfoId,
            msgId = sykmeldingId,
            sykmeldingId = sykmeldingId,
            journalpostId = oppgave?.journalpostId,
        )
    }
}

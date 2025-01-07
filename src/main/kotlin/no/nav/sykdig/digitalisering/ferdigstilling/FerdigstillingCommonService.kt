package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.config.kafka.OK_SYKMELDING_TOPIC
import no.nav.sykdig.digitalisering.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.dokument.DocumentService
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.mapToReceivedSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.sykmelding.ReceivedSykmelding
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import no.nav.sykdig.utils.createTitle
import no.nav.sykdig.utils.createTitleNavNo
import no.nav.sykdig.utils.createTitleRina
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class FerdigstillingCommonService {

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

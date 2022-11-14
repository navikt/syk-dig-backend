package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.logger
import no.nav.sykdig.oppgavemottak.kafka.okSykmeldingTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class FerdigstillingService(
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient,
    private val dokarkivClient: DokarkivClient,
    private val oppgaveClient: OppgaveClient,
    private val sykmeldingOKProducer: KafkaProducer<String, ReceivedSykmelding>
) {
    val log = logger()

    fun ferdigstill(
        oppgaveId: String,
        navnSykmelder: String?,
        land: String,
        fnr: String,
        enhet: String,
        dokumentinfoId: String,
        journalpostId: String,
        sykmeldingId: String,
        receivedSykmelding: ReceivedSykmelding
    ) {
        if (safJournalpostGraphQlClient.erFerdigstilt(journalpostId)) {
            log.info("Journalpost med id $journalpostId er allerede ferdigstilt, sykmeldingId $sykmeldingId")
        } else {
            dokarkivClient.oppdaterOgFerdigstillJournalpost(
                navnSykmelder = navnSykmelder,
                land = land,
                fnr = fnr,
                enhet = enhet,
                dokumentinfoId = dokumentinfoId,
                journalpostId = journalpostId,
                sykmeldingId = sykmeldingId
            )
        }
        oppgaveClient.ferdigstillOppgave(oppgaveId = oppgaveId, sykmeldingId = sykmeldingId)

        try {
            sykmeldingOKProducer.send(
                ProducerRecord(okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding)
            ).get()
            log.info("Sykmelding sendt to kafka topic {} sykmelding id {}", okSykmeldingTopic, receivedSykmelding.sykmelding.id)
        } catch (exception: Exception) {
            log.error("failed to send sykmelding to kafka result for sykmelding {}", receivedSykmelding.sykmelding.id)
            throw exception
        }
    }
}

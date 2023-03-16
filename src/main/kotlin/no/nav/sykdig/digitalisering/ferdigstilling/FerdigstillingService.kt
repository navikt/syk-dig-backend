package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.syfo.model.ReceivedSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.mapToReceivedSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.logger
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.oppgavemottak.kafka.okSykmeldingTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class FerdigstillingService(
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient,
    private val dokarkivClient: DokarkivClient,
    private val oppgaveClient: OppgaveClient,
    private val sykmeldingOKProducer: KafkaProducer<String, ReceivedSykmelding>,
) {
    val log = logger()

    fun ferdigstill(
        navnSykmelder: String?,
        enhet: String,
        oppgave: OppgaveDbModel,
        sykmeldt: Person,
        validatedValues: FerdistilltRegisterOppgaveValues,
    ) {
        requireNotNull(oppgave.dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }
        val receivedSykmelding = mapToReceivedSykmelding(
            ferdigstillteRegisterOppgaveValues = validatedValues,
            sykmeldt = sykmeldt,
            harAndreRelevanteOpplysninger = validatedValues.harAndreRelevanteOpplysninger,
            sykmeldingId = oppgave.sykmeldingId.toString(),
            journalpostId = oppgave.journalpostId,
            opprettet = oppgave.opprettet.toLocalDateTime(),
        )
        if (safJournalpostGraphQlClient.erFerdigstilt(oppgave.journalpostId)) {
            log.info("Journalpost med id ${oppgave.journalpostId} er allerede ferdigstilt, sykmeldingId ${oppgave.sykmeldingId}")
        } else {
            dokarkivClient.oppdaterOgFerdigstillJournalpost(
                navnSykmelder = navnSykmelder,
                land = validatedValues.skrevetLand,
                fnr = sykmeldt.fnr,
                enhet = enhet,
                dokumentinfoId = oppgave.dokumentInfoId,
                journalpostId = oppgave.journalpostId,
                sykmeldingId = oppgave.sykmeldingId.toString(),
                perioder = receivedSykmelding.sykmelding.perioder,
                source = oppgave.source
            )
        }
        oppgaveClient.ferdigstillOppgave(oppgaveId = oppgave.oppgaveId, sykmeldingId = oppgave.sykmeldingId.toString())

        try {
            sykmeldingOKProducer.send(
                ProducerRecord(okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding),
            ).get()
            log.info(
                "Sykmelding sendt to kafka topic {} sykmelding id {}",
                okSykmeldingTopic,
                receivedSykmelding.sykmelding.id,
            )
        } catch (exception: Exception) {
            log.error("failed to send sykmelding to kafka result for sykmelding {}", receivedSykmelding.sykmelding.id)
            throw exception
        }
    }
}

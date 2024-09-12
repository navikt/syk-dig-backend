package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.sykdig.applog
import no.nav.sykdig.config.kafka.OK_SYKMLEDING_TOPIC
import no.nav.sykdig.digitalisering.dokarkiv.DokarkivClient
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
    val log = applog()
    val securelog = securelog()

    fun ferdigstill(
        enhet: String,
        oppgave: OppgaveDbModel,
        sykmeldt: Person,
        validatedValues: FerdistilltRegisterOppgaveValues,
    ) {
        requireNotNull(oppgave.dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }
        val receivedSykmelding =
            mapToReceivedSykmelding(
                ferdigstillteRegisterOppgaveValues = validatedValues,
                sykmeldt = sykmeldt,
                sykmeldingId = oppgave.sykmeldingId.toString(),
                journalpostId = oppgave.journalpostId,
                opprettet = oppgave.opprettet.toLocalDateTime(),
            )
        val journalpost = safJournalpostGraphQlClient.getJournalpost(oppgave.journalpostId)
        securelog.info("journalpostid ${oppgave.journalpostId} ble hentet: ${objectMapper.writeValueAsString(journalpost)}")
        if (safJournalpostGraphQlClient.erFerdigstilt(journalpost)) {
            log.info("Journalpost med id ${oppgave.journalpostId} er allerede ferdigstilt, sykmeldingId ${oppgave.sykmeldingId}")
        }
        val hentAvvsenderMottar = safJournalpostGraphQlClient.getAvvsenderMottar(journalpost)
        dokarkivClient.oppdaterOgFerdigstillJournalpost(
            landAlpha3 = validatedValues.skrevetLand,
            fnr = sykmeldt.fnr,
            enhet = enhet,
            dokumentinfoId = oppgave.dokumentInfoId,
            journalpostId = oppgave.journalpostId,
            sykmeldingId = oppgave.sykmeldingId.toString(),
            perioder = receivedSykmelding.sykmelding.perioder,
            source = oppgave.source,
            avvisningsGrunn = null,
            sykmeldtNavn = sykmeldt.navn.toFormattedNameString(),
            orginalAvsenderMottaker = hentAvvsenderMottar,
        )

        oppgaveClient.ferdigstillOppgave(oppgaveId = oppgave.oppgaveId, sykmeldingId = oppgave.sykmeldingId.toString())

        try {
            sykmeldingOKProducer.send(
                ProducerRecord(OK_SYKMLEDING_TOPIC, receivedSykmelding.sykmelding.id, receivedSykmelding),
            ).get()
            log.info(
                "Sykmelding sendt to kafka topic {} sykmelding id {}",
                OK_SYKMLEDING_TOPIC,
                receivedSykmelding.sykmelding.id,
            )
        } catch (exception: Exception) {
            log.error("failed to send sykmelding to kafka result for sykmelding {}", receivedSykmelding.sykmelding.id)
            throw exception
        }
    }

    fun ferdigstillAvvistJournalpost(
        enhet: String,
        oppgave: OppgaveDbModel,
        sykmeldt: Person,
        avvisningsGrunn: String,
    ) {
        requireNotNull(oppgave.dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }
        val journalpost = safJournalpostGraphQlClient.getJournalpost(oppgave.journalpostId)
        securelog.info("journalpostid ${oppgave.journalpostId} ble hentet: ${objectMapper.writeValueAsString(journalpost)}")

        if (safJournalpostGraphQlClient.erFerdigstilt(journalpost)) {
            log.info("Journalpost med id ${oppgave.journalpostId} er allerede ferdigstilt, sykmeldingId ${oppgave.sykmeldingId}")
        } else {
            val hentAvvsenderMottar = safJournalpostGraphQlClient.getAvvsenderMottar(journalpost)
            dokarkivClient.oppdaterOgFerdigstillJournalpost(
                landAlpha3 = null,
                fnr = sykmeldt.fnr,
                enhet = enhet,
                dokumentinfoId = oppgave.dokumentInfoId,
                journalpostId = oppgave.journalpostId,
                sykmeldingId = oppgave.sykmeldingId.toString(),
                perioder = null,
                source = oppgave.source,
                avvisningsGrunn = avvisningsGrunn,
                sykmeldtNavn = sykmeldt.navn.toFormattedNameString(),
                orginalAvsenderMottaker = hentAvvsenderMottar,
            )
        }
    }
}

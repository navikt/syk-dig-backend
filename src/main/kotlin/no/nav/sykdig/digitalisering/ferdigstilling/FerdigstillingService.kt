package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.digitalisering.ValidatedOppgaveValues
import no.nav.sykdig.digitalisering.ferdigstilling.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.extractHelseOpplysningerArbeidsuforhet
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.fellesformatMarshaller
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.get
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.mapToFellesformat
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.toString
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.toSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.logger
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
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
        navnSykmelder: String?,
        land: String,
        enhet: String,
        oppgave: DigitaliseringsoppgaveDbModel,
        sykmeldt: Person,
        validatedValues: ValidatedOppgaveValues,
        harAndreRelevanteOpplysninger: Boolean?
    ) {
        requireNotNull(oppgave.dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }
        val sykmeldingId = oppgave.sykmeldingId.toString()
        val receivedSykmelding = mapToReceivedSykmelding(
            validatedValues = validatedValues,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            harAndreRelevanteOpplysninger = harAndreRelevanteOpplysninger
        )
        if (safJournalpostGraphQlClient.erFerdigstilt(oppgave.journalpostId)) {
            log.info("Journalpost med id ${oppgave.journalpostId} er allerede ferdigstilt, sykmeldingId $sykmeldingId")
        } else {
            dokarkivClient.oppdaterOgFerdigstillJournalpost(
                navnSykmelder = navnSykmelder,
                land = land,
                fnr = sykmeldt.fnr,
                enhet = enhet,
                dokumentinfoId = oppgave.dokumentInfoId,
                journalpostId = oppgave.journalpostId,
                sykmeldingId = sykmeldingId
            )
        }
        oppgaveClient.ferdigstillOppgave(oppgaveId = oppgave.oppgaveId, sykmeldingId = sykmeldingId)

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

    fun mapToReceivedSykmelding(
        validatedValues: ValidatedOppgaveValues,
        oppgave: DigitaliseringsoppgaveDbModel,
        sykmeldt: Person,
        harAndreRelevanteOpplysninger: Boolean?
    ): ReceivedSykmelding {

        val fellesformat = mapToFellesformat(
            oppgave = oppgave,
            validatedValues = validatedValues,
            person = sykmeldt,
            sykmeldingId = oppgave.sykmeldingId.toString(),
            datoOpprettet = oppgave.opprettet.toLocalDateTime(),
            journalpostId = oppgave.journalpostId
        )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = oppgave.sykmeldingId.toString(),
            pasientAktoerId = "",
            legeAktoerId = "",
            msgId = oppgave.sykmeldingId.toString(),
            signaturDato = msgHead.msgInfo.genDate
        )

        return ReceivedSykmelding(
            sykmelding = sykmelding,
            personNrPasient = sykmeldt.fnr,
            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
            personNrLege = "",
            navLogId = oppgave.sykmeldingId.toString(),
            msgId = oppgave.sykmeldingId.toString(),
            legekontorOrgNr = null,
            legekontorOrgName = "",
            legekontorHerId = null,
            legekontorReshId = null,
            mottattDato = oppgave.opprettet.toLocalDateTime(),
            rulesetVersion = healthInformation.regelSettVersjon,
            fellesformat = fellesformatMarshaller.toString(fellesformat),
            tssid = null,
            merknader = null,
            partnerreferanse = null,
            legeHelsepersonellkategori = null,
            legeHprNr = null,
            vedlegg = null,
            utenlandskSykmelding = UtenlandskSykmelding(
                validatedValues.skrevetLand,
                harAndreRelevanteOpplysninger ?: false
            )
        )
    }
}

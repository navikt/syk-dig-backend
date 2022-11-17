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
import java.time.LocalDateTime

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
        harAndreRelevanteOpplysninger: Boolean?,
        sykmeldingId: String,
        journalpostId: String,
        opprettet: LocalDateTime,
        dokumentInfoId: String?,
        oppgaveId: String
    ) {
        requireNotNull(dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }
        val receivedSykmelding = mapToReceivedSykmelding(
            validatedValues = validatedValues,
            sykmeldt = sykmeldt,
            harAndreRelevanteOpplysninger = harAndreRelevanteOpplysninger,
            sykmeldingId = sykmeldingId,
            journalpostId = journalpostId,
            opprettet = opprettet
        )
        if (safJournalpostGraphQlClient.erFerdigstilt(journalpostId)) {
            log.info("Journalpost med id $journalpostId er allerede ferdigstilt, sykmeldingId $sykmeldingId")
        } else {
            dokarkivClient.oppdaterOgFerdigstillJournalpost(
                navnSykmelder = navnSykmelder,
                land = land,
                fnr = sykmeldt.fnr,
                enhet = enhet,
                dokumentinfoId = dokumentInfoId,
                journalpostId = journalpostId,
                sykmeldingId = sykmeldingId
            )
        }
        oppgaveClient.ferdigstillOppgave(oppgaveId = oppgaveId, sykmeldingId = sykmeldingId)

        try {
            sykmeldingOKProducer.send(
                ProducerRecord(okSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding)
            ).get()
            log.info(
                "Sykmelding sendt to kafka topic {} sykmelding id {}",
                okSykmeldingTopic,
                receivedSykmelding.sykmelding.id
            )
        } catch (exception: Exception) {
            log.error("failed to send sykmelding to kafka result for sykmelding {}", receivedSykmelding.sykmelding.id)
            throw exception
        }
    }

    fun mapToReceivedSykmelding(
        validatedValues: ValidatedOppgaveValues,
        sykmeldt: Person,
        harAndreRelevanteOpplysninger: Boolean?,
        sykmeldingId: String,
        journalpostId: String,
        opprettet: LocalDateTime
    ): ReceivedSykmelding {

        val fellesformat = mapToFellesformat(
            validatedValues = validatedValues,
            person = sykmeldt,
            sykmeldingId = sykmeldingId,
            datoOpprettet = opprettet,
            journalpostId = journalpostId
        )

        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
        val msgHead = fellesformat.get<XMLMsgHead>()

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = sykmeldingId,
            pasientAktoerId = "",
            msgId = sykmeldingId,
            signaturDato = msgHead.msgInfo.genDate
        )

        return ReceivedSykmelding(
            sykmelding = sykmelding,
            personNrPasient = sykmeldt.fnr,
            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
            personNrLege = "",
            navLogId = sykmeldingId,
            msgId = sykmeldingId,
            legekontorOrgNr = null,
            legekontorOrgName = "",
            legekontorHerId = null,
            legekontorReshId = null,
            mottattDato = opprettet,
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

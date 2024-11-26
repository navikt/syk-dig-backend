package no.nav.sykdig.digitalisering.ferdigstilling

import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.config.kafka.OK_SYKMELDING_TOPIC
import no.nav.sykdig.digitalisering.dokarkiv.DokarkivClient
import no.nav.sykdig.digitalisering.dokument.DocumentService
import no.nav.sykdig.digitalisering.ferdigstilling.mapping.mapToReceivedSykmelding
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
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
class FerdigstillingService(
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient,
    private val dokarkivClient: DokarkivClient,
    private val oppgaveClient: OppgaveClient,
    private val sykmeldingOKProducer: KafkaProducer<String, ReceivedSykmelding>,
    private val dokumentService: DocumentService,
    private val sykmelderService: SykmelderService,
) {
    val log = applog()
    val securelog = securelog()

    fun ferdigstillUtenlandskOppgave(
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
            updateAvvistTitle(oppgave, receivedSykmelding)
        } else {
            val hentAvvsenderMottar = safJournalpostGraphQlClient.getAvsenderMottar(journalpost)
            dokarkivClient.oppdaterOgFerdigstillUtenlandskJournalpost(
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
        }

        oppgaveClient.ferdigstillOppgave(oppgaveId = oppgave.oppgaveId, sykmeldingId = oppgave.sykmeldingId.toString())
        updateTitle(oppgave, receivedSykmelding)

        try {
            sykmeldingOKProducer.send(
                ProducerRecord(OK_SYKMELDING_TOPIC, receivedSykmelding.sykmelding.id, receivedSykmelding),
            ).get()
            log.info(
                "Sykmelding sendt to kafka topic {} sykmelding id {}",
                OK_SYKMELDING_TOPIC,
                receivedSykmelding.sykmelding.id,
            )
        } catch (exception: Exception) {
            log.error("failed to send sykmelding to kafka result for sykmelding {}", receivedSykmelding.sykmelding.id)
            throw exception
        }
    }

    private fun updateTitle(
        oppgave: OppgaveDbModel,
        receivedSykmelding: ReceivedSykmelding
    ) {
        updateDocumentTitle(oppgave, receivedSykmelding)
    }

    private fun updateAvvistTitle(
        oppgave: OppgaveDbModel,
        receivedSykmelding: ReceivedSykmelding
    ) {
        updateDocumentTitle(oppgave, receivedSykmelding, isAvvist = true)
    }

    private fun updateDocumentTitle(
        oppgave: OppgaveDbModel,
        receivedSykmelding: ReceivedSykmelding,
        isAvvist: Boolean = false
    ) {
        securelog.info("documents: ${oppgave.dokumenter.map { it.tittel }} source: ${oppgave.source} sykmeldignId: ${receivedSykmelding.sykmelding.id} ")

        val dokument = when {
            isAvvist -> oppgave.dokumenter.firstOrNull { it.tittel.lowercase().startsWith("avvist") }
            else -> oppgave.dokumenter.firstOrNull()
        }

        if (dokument != null) {
            log.info("found ${if (isAvvist) "avvist " else ""}document, updating title")
            val tittel = when (oppgave.source) {
                "rina" ->
                    createTitleRina(
                        perioder = receivedSykmelding.sykmelding.perioder,
                        avvisningsGrunn = oppgave.avvisingsgrunn,
                    )
                "navno" ->
                    createTitleNavNo(
                        perioder = receivedSykmelding.sykmelding.perioder,
                        avvisningsGrunn = oppgave.avvisingsgrunn,
                    )
                else ->
                    createTitle(
                        perioder = receivedSykmelding.sykmelding.perioder,
                        avvisningsGrunn = oppgave.avvisingsgrunn,
                    )
            }
            dokumentService.updateDocumentTitle(
                oppgaveId = oppgave.oppgaveId,
                dokumentInfoId = dokument.dokumentInfoId,
                tittel = tittel,
            )
        }
    }

    fun ferdigstillUtenlandskAvvistJournalpost(
        enhet: String,
        oppgave: OppgaveDbModel,
        sykmeldt: Person,
        avvisningsGrunn: String?,
    ) {
        requireNotNull(oppgave.dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }
        val journalpost = safJournalpostGraphQlClient.getJournalpost(oppgave.journalpostId)
        securelog.info("journalpostid ${oppgave.journalpostId} ble hentet: ${objectMapper.writeValueAsString(journalpost)}")

        if (safJournalpostGraphQlClient.erFerdigstilt(journalpost)) {
            log.info("Journalpost med id ${oppgave.journalpostId} er allerede ferdigstilt, sykmeldingId ${oppgave.sykmeldingId}")
        } else {
            val hentAvsenderMottar = safJournalpostGraphQlClient.getAvsenderMottar(journalpost)
            dokarkivClient.oppdaterOgFerdigstillUtenlandskJournalpost(
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
                orginalAvsenderMottaker = hentAvsenderMottar,
            )
        }
    }

    //TODO() implement this - lage eigen service?
    suspend fun ferdigstillNasjonalAvvistJournalpost(
        enhet: String,
        oppgave: NasjonalManuellOppgaveDAO,
        sykmeldt: Person,
        avvisningsGrunn: String?,
        loggingMeta: LoggingMeta,
    ) {
        requireNotNull(oppgave.dokumentInfoId) { "DokumentInfoId må være satt for å kunne ferdigstille oppgave" }
        val journalpost = safJournalpostGraphQlClient.getJournalpost(oppgave.journalpostId)
        securelog.info("journalpostid ${oppgave.journalpostId} ble hentet: ${objectMapper.writeValueAsString(journalpost)}")

        if (safJournalpostGraphQlClient.erFerdigstilt(journalpost)) {
            log.info("Journalpost med id ${oppgave.journalpostId} er allerede ferdigstilt, sykmeldingId ${oppgave.sykmeldingId}")
        }
        else {
            log.info("Ferdigstiller journalpost med id ${oppgave.journalpostId},  dokumentInfoId ${oppgave.dokumentInfoId}, sykmeldingId ${oppgave.sykmeldingId} og oppgaveId ${oppgave.oppgaveId}")
            dokarkivClient.oppdaterOgFerdigstillNasjonalJournalpost(
                journalpostId = oppgave.journalpostId,
                dokumentInfoId = oppgave.dokumentInfoId,
                pasientFnr = oppgave.fnr!!,
                sykmeldingId = oppgave.sykmeldingId,
                sykmelder = sykmelderService.getSykmelder(oppgave.papirSmRegistrering.behandler?.hpr!!, "callId"),
                loggingMeta = loggingMeta,
                navEnhet = enhet,
                avvist = true,
                perioder = oppgave.papirSmRegistrering.perioder ?: emptyList(),
            )
        }
    }

    fun sendUpdatedSykmelding(oppgave: OppgaveDbModel, sykmeldt: Person, navEmail: String, values: FerdistilltRegisterOppgaveValues) {
        val receivedSykmelding =
            mapToReceivedSykmelding(
                ferdigstillteRegisterOppgaveValues = values,
                sykmeldt = sykmeldt,
                sykmeldingId = oppgave.sykmeldingId.toString(),
                journalpostId = oppgave.journalpostId,
                opprettet = oppgave.opprettet.toLocalDateTime(),
            )
        sykmeldingOKProducer.send(
            ProducerRecord(OK_SYKMELDING_TOPIC, receivedSykmelding.sykmelding.id, receivedSykmelding),
        ).get()
        log.info("sendt oppdatert sykmelding med id ${receivedSykmelding.sykmelding.id}")
        updateTitle(oppgave, receivedSykmelding)
    }
}

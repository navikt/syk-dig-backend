package no.nav.sykdig.utenlandsk.services

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.db.OppgaveRepository
import no.nav.sykdig.utenlandsk.db.toSykmelding
import no.nav.sykdig.shared.exceptions.NoOppgaveException
import no.nav.sykdig.oppgave.models.AllOppgaveResponse
import no.nav.sykdig.oppgave.models.AllOppgaveType
import no.nav.sykdig.oppgave.OppgaveClient
import no.nav.sykdig.utenlandsk.models.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.utenlandsk.models.RegisterOppgaveValues
import no.nav.sykdig.pdl.Person
import no.nav.sykdig.saf.graphql.DokumentInfo
import no.nav.sykdig.saf.graphql.SafJournalpost
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.utenlandsk.models.DokumentDbModel
import no.nav.sykdig.utenlandsk.models.OppgaveDbModel
import no.nav.sykdig.shared.securelog
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import no.nav.sykdig.shared.utils.getLoggingMeta
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Service
class SykDigOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val ferdigstillingService: FerdigstillingService,
    private val oppgaveClient: OppgaveClient,
) {
    private val log = applog()
    private val securelog = securelog()


    suspend fun opprettOgLagreOppgave(
        journalpost: SafJournalpost,
        journalpostId: String,
        fnr: String,
        aktoerId: String,
        navEpost: String,
    ): String {
        val opprettetOppgave = oppgaveClient.opprettOppgave(
            journalpostId = journalpostId,
            aktoerId = aktoerId,
        )
        val oppgaveId = opprettetOppgave.id.toString()

        val oppgave = buildOppgaveModel(
            oppgaveId = oppgaveId,
            journalpost = journalpost,
            journalpostId = journalpostId,
            fnr = fnr,
            navEpost = navEpost,
        )

        oppgaveRepository.lagreOppgave(oppgave)
        log.info("Oppgave med id $oppgaveId lagret")
        return oppgaveId
    }

    fun getOppgaveFromSykmeldingId(sykmeldingId: String): OppgaveDbModel {
        val oppgave = oppgaveRepository.getOppgaveBySykmeldingId(sykmeldingId)
        val loggingMeta = getLoggingMeta(sykmeldingId, oppgave)
        if (oppgave == null) {
            log.warn("Fant ikke oppgave {}", StructuredArguments.fields(loggingMeta))
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }
        log.info("Hentet oppgave {}", StructuredArguments.fields(loggingMeta))
        return oppgave
    }

    fun getOppgave(oppgaveId: String): OppgaveDbModel {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        val loggingMeta = oppgave?.sykmelding?.sykmelding?.id?.let { getLoggingMeta(it, oppgave) }
        if (oppgave == null) {
            log.warn("Fant ikke oppgave {} ", StructuredArguments.fields(loggingMeta))
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }
        log.info("Hentet oppgave {} ", StructuredArguments.fields(loggingMeta))
        return oppgave
    }

    suspend fun ferdigstillExistingJournalfoeringsoppgave(
        journalpostId: String,
        journalpost: SafJournalpost,
    ) {
        log.info(
            "Henter eksisterende journalføringsoppgave for å ferdigstille før vi digitaliserer utenlandsk/papirsykmelding {}",
            kv("journalpostId", journalpostId),
        )
        val existingOppgave = getExistingOppgave(journalpostId, journalpost)
        if (existingOppgave == null) {
            log.warn("oppgave er null, får ikke lukket oppgave {}", kv("journalpostId", journalpostId))
            return
        }
        if (existingOppgave.id == null) {
            log.warn("oppgaveId er null, får ikke lukket oppgave {}", kv("journalpostId", journalpostId))
            return
        }
        log.info(
            "Prøver å ferdigstille eksisterende journalføringsoppgave {} {}",
            kv("journalpostId", journalpostId),
            kv("oppgaveId", existingOppgave.id),
        )
        oppgaveClient.ferdigstillJournalføringsoppgave(existingOppgave.id, existingOppgave.versjon, journalpostId)
        log.info(
            "Ferdigstilt journalføringsoppgave {} {}",
            kv("journalpostId", journalpostId),
            kv("oppgaveId", existingOppgave.id),
        )
        securelog.info(
            "Ferdigstilt journalføringsoppgave {} {} {}",
            kv("journalpostId", journalpostId),
            kv("oppgaveId", existingOppgave.id),
            kv("aktørId", existingOppgave.aktoerId),
        )
    }

    suspend fun getExistingOppgave(
        journalpostId: String,
        journalpost: SafJournalpost,
    ): AllOppgaveResponse? {
        try {
            val oppgaver = oppgaveClient.getOppgaver(journalpostId, journalpost)
            log.info("hentet ${oppgaver.size}, på journalpostId $journalpostId")
            val filtrerteOppgaver =
                oppgaver.filter {
                    (it.tema == "SYM" || it.tema == "SYK") && it.oppgavetype == AllOppgaveType.JFR.name
                }
            if (filtrerteOppgaver.size != 1) {
                val oppgaverInfo =
                    filtrerteOppgaver.joinToString(separator = ", ", prefix = "[", postfix = "]") { oppgave ->
                        "id=${oppgave.id}, status=${oppgave.status}, tildeltEnhetsnr=${oppgave.tildeltEnhetsnr}"
                    }
                log.warn(
                    "Antall eksisterende filtrerteOppgaver er enten for mange eller for få til at vi kan lukke de {} {} {}",
                    kv("journalpostId", journalpostId),
                    kv("antall filtrerteOppgaver", filtrerteOppgaver.size),
                    kv("info", oppgaverInfo),
                )
                return null
            }
            return filtrerteOppgaver.single()
        } catch (e: NoOppgaveException) {
            log.error(
                "klarte ikke hente oppgave(r) tilhørende journalpostId $journalpostId",
                e,
            )
            throw e
        }
    }

    fun updateOppgave(
        oppgaveId: String,
        registerOppgaveValues: RegisterOppgaveValues,
        navEpost: String,
    ) {
        val oppgave = getOppgave(oppgaveId)
        val sykmelding = toSykmelding(oppgave, registerOppgaveValues)
        oppgaveRepository.updateOppgave(oppgave, sykmelding, navEpost, false)
    }

    fun ferdigstillOppgaveGosys(
        oppgave: OppgaveDbModel,
        navEpost: String,
    ) {
        val sykmelding = oppgaveRepository.getLastSykmelding(oppgave.oppgaveId)
        oppgaveRepository.ferdigstillOppgaveGosys(oppgave, navEpost, sykmelding)
    }

    fun ferdigstillAvvistOppgave(
        oppgave: OppgaveDbModel,
        navEpost: String,
        enhetId: String,
        sykmeldt: Person,
        avvisningsgrunn: Avvisingsgrunn,
        avvisningsgrunnAnnet: String?,
    ) {
        val sykmelding = oppgaveRepository.getLastSykmelding(oppgave.oppgaveId)
        oppgaveRepository.ferdigstillAvvistOppgave(oppgave, navEpost, sykmelding, avvisningsgrunn)
        ferdigstillingService.ferdigstillUtenlandskAvvistJournalpost(
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            avvisningsGrunn = mapAvvisningsgrunn(avvisningsgrunn, avvisningsgrunnAnnet),
        )
    }

    @Transactional
    suspend fun ferdigstillUtenlandskAvvistOppgave(
        oppgave: OppgaveDbModel,
        navEpost: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String,
        sykmeldt: Person,
    ) {
        val sykmelding = toSykmelding(oppgave, values)

        oppgaveRepository.updateOppgave(oppgave, sykmelding, navEpost, true)
        ferdigstillingService.ferdigstillUtenlandskOppgave(
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            validatedValues = values,
        )
    }

    @Transactional
    fun oppdaterSykmelding(oppgave: OppgaveDbModel, navEmail: String, values: FerdistilltRegisterOppgaveValues, enhetId: String, sykmeldt: Person) {
        val sykmelding = toSykmelding(oppgave, values)
        oppgaveRepository.updateSykmelding(oppgave, navEmail, sykmelding)
        log.info("updated sykmelding in db")

        ferdigstillingService.sendUpdatedSykmelding(oppgave, sykmeldt, navEmail, values)
    }

    private fun buildOppgaveModel(
        oppgaveId: String,
        journalpost: SafJournalpost,
        journalpostId: String,
        fnr: String,
        navEpost: String,
    ): OppgaveDbModel {
        val dokumentInfoId = journalpost.dokumenter.first().dokumentInfoId
        val dokumenter = buildDokumenter(journalpost.dokumenter)
        val source = determineSource(journalpost)

        return OppgaveDbModel(
            oppgaveId = oppgaveId,
            fnr = fnr,
            journalpostId = journalpostId,
            dokumentInfoId = dokumentInfoId,
            dokumenter = dokumenter,
            opprettet = OffsetDateTime.now(ZoneOffset.UTC),
            ferdigstilt = null,
            tilbakeTilGosys = false,
            avvisingsgrunn = null,
            sykmeldingId = UUID.randomUUID(),
            type = UTLAND,
            sykmelding = null,
            endretAv = navEpost,
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            source = source,
        )
    }

    private fun buildDokumenter(dokumenter: List<DokumentInfo>): List<DokumentDbModel> {
        return dokumenter.map {
            val oppdatertTittel = if (it.tittel == "Utenlandsk sykmelding") {
                "Digitalisert utenlandsk sykmelding"
            } else {
                it.tittel ?: "Mangler Tittel"
            }
            DokumentDbModel(it.dokumentInfoId, oppdatertTittel)
        }
    }

    private fun determineSource(journalpost: SafJournalpost): String {
        val isEgenerklaering = journalpost.tittel?.lowercase()?.contains("egenerklæring") ?: false
        return when {
            journalpost.kanal == "NAV_NO" || isEgenerklaering -> "navno"
            journalpost.kanal == "RINA" -> "rina"
            else -> "syk-dig"
        }
    }

    companion object {
        private const val UTLAND = "UTLAND"
    }
}

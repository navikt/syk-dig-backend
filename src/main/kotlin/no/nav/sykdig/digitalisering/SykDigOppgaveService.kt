package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.applog
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.db.toSykmelding
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.AllOppgaveResponse
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.AllOppgaveType
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import no.nav.sykdig.digitalisering.tilgangskontroll.OppgaveSecurityService
import no.nav.sykdig.digitalisering.tilgangskontroll.getNavEmail
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.securelog
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    private fun createOppgave(oppgaveId: String, fnr: String, journalpostId: String, journalpost: SafJournalpost, dokumentInfoId: String, source: String = "syk-dig"): OppgaveDbModel {
        val dokumenter = journalpost.dokumenter.map {
            val oppdatertTittel = if (it.tittel == "Utenlandsk sykmelding") {
                "Digitalisert utenlandsk sykmelding"
            } else {
                it.tittel ?: "Mangler Tittel"
            }
            DokumentDbModel(it.dokumentInfoId, oppdatertTittel)
        }

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
            endretAv = getNavEmail(),
            timestamp = OffsetDateTime.now(ZoneOffset.UTC),
            source = source,
        )
    }

    fun opprettOgLagreOppgave(
        journalpost: SafJournalpost,
        journalpostId: String,
        fnr: String,
        aktoerId: String,
    ): String {
        val response =
            oppgaveClient.opprettOppgave(
                journalpostId = journalpostId,
                aktoerId = aktoerId,
            )

        val oppgaveId = response.id.toString()
        val dokumentInfoId = journalpost.dokumenter.first().dokumentInfoId
        val tittel = journalpost.tittel.lowercase().contains("egenerklæring")
        securelog.info("is egenarklaring: $tittel journalpostId: $journalpostId")
        val oppgave = createOppgave(
            oppgaveId = oppgaveId,
            fnr = fnr,
            journalpostId = journalpostId,
            journalpost = journalpost,
            dokumentInfoId = dokumentInfoId,
            source = if (journalpost.kanal == "NAV_NO" || tittel) "navno" else if (journalpost.kanal == "RINA") "rina" else "syk-dig"
        )
        oppgaveRepository.lagreOppgave(oppgave)
        log.info("Oppgave med id $oppgaveId lagret")
        return oppgaveId
    }

    fun getOppgaveFromSykmeldingId(sykmeldingId: String): OppgaveDbModel {
        val oppgave = oppgaveRepository.getOppgaveBySykmeldingId(sykmeldingId)
        if (oppgave == null) {
            log.warn("Fant ikke oppgave med sykmeldingId $sykmeldingId")
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }
        log.info("Hentet oppgave med sykmelding id $sykmeldingId")
        return oppgave
    }

    fun getOppgave(oppgaveId: String): OppgaveDbModel {
        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave == null) {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }
        log.info("Hentet oppgave med id $oppgaveId")
        return oppgave
    }

    fun ferdigstillExistingJournalfoeringsoppgave(
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

    fun getExistingOppgave(
        journalpostId: String,
        journalpost: SafJournalpost,
    ): AllOppgaveResponse? {
        try {
            val oppgaver = oppgaveClient.getOppgaver(journalpostId, journalpost)
            log.info("hentet ${oppgaver.size}, på journalpostId $journalpostId")
            val filtrerteOppgaver =
                oppgaver.filter {
                    (it.tema == "SYM" || it.tema == "SYK") && it.oppgavetype == AllOppgaveType.JFR
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
        ferdigstillingService.ferdigstillAvvistJournalpost(
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            avvisningsGrunn = mapAvvisningsgrunn(avvisningsgrunn, avvisningsgrunnAnnet),
        )
    }

    @Transactional
    fun ferdigstillUtenlandskAvvistOppgave(
        oppgave: OppgaveDbModel,
        navEpost: String,
        values: FerdistilltRegisterOppgaveValues,
        enhetId: String,
        sykmeldt: Person,
    ) {
        val sykmelding = toSykmelding(oppgave, values)

        oppgaveRepository.updateOppgave(oppgave, sykmelding, navEpost, true)
        ferdigstillingService.ferdigstill(
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            validatedValues = values,
        )
    }

    fun ferdigstillNasjonalAvvistOppgave(
        oppgave: OppgaveDbModel,
        navEpost: String,
        enhetId: String,
        sykmeldt: Person,
        avvisningsgrunn: Avvisingsgrunn?,
        avvisningsgrunnAnnet: String?,
    ) {
        ferdigstillingService.ferdigstillAvvistJournalpost(
            enhet = enhetId,
            oppgave = oppgave,
            sykmeldt = sykmeldt,
            avvisningsGrunn = avvisningsgrunn?.let { mapAvvisningsgrunn(it, avvisningsgrunnAnnet) },
        )
    }

    @Transactional
    fun oppdaterSykmelding(oppgave: OppgaveDbModel, navEmail: String, values: FerdistilltRegisterOppgaveValues, enhetId: String, sykmeldt: Person) {
        val sykmelding = toSykmelding(oppgave, values)
        oppgaveRepository.updateSykmelding(oppgave, navEmail, sykmelding)
        log.info("updated sykmelding in db")

        ferdigstillingService.sendUpdatedSykmelding(oppgave, sykmeldt, navEmail, values)
    }

    companion object {
        private const val UTLAND = "UTLAND"
    }
}

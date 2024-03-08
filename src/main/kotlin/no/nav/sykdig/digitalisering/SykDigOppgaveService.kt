package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.applog
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.db.toSykmelding
import no.nav.sykdig.digitalisering.exceptions.NoOppgaveException
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveClient
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveType
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.TempOppgaveResponse
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import no.nav.sykdig.digitalisering.tilgangskontroll.getNavEmail
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.model.DokumentDbModel
import no.nav.sykdig.model.OppgaveDbModel
import no.nav.sykdig.securelog
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class SykDigOppgaveService(
    private val oppgaveRepository: OppgaveRepository,
    private val ferdigstillingService: FerdigstillingService,
    private val oppgaveClient: OppgaveClient,
) {
    private val log = applog()
    private val securelog = securelog()

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

        val dokumenter =
            journalpost.dokumenter.map {
                DokumentDbModel(it.dokumentInfoId, it.tittel ?: "Mangler Tittel")
            }

        val oppgaveId = response.id.toString()
        val oppgave =
            OppgaveDbModel(
                oppgaveId = oppgaveId,
                fnr = fnr,
                journalpostId = journalpostId,
                dokumentInfoId = journalpost.dokumenter.first().dokumentInfoId,
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
                source = "syk-dig",
            )
        oppgaveRepository.lagreOppgave(oppgave)
        return oppgaveId
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
    ): TempOppgaveResponse? {
        try {
            val oppgaver =
                oppgaveClient.getOppgaver(journalpostId, journalpost).filter {
                    (it.tema == "SYM" || it.tema == "SYK") && it.oppgavetype == OppgaveType.JFR
                }
            if (oppgaver.size != 1) {
                val oppgaverInfo =
                    oppgaver.joinToString(separator = ", ", prefix = "[", postfix = "]") { oppgave ->
                        "id=${oppgave.id}, status=${oppgave.status}, tildeltEnhetsnr=${oppgave.tildeltEnhetsnr}"
                    }
                log.warn(
                    "Antall eksisterende oppgaver er enten for mange eller for få til at vi kan lukke de {} {} {}",
                    kv("journalpostId", journalpostId),
                    kv("antall oppgaver", oppgaver.size),
                    kv("info", oppgaverInfo),
                )
                return null
            }
            return oppgaver.single()
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
    fun ferdigstillOppgave(
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

    companion object {
        private const val UTLAND = "UTLAND"
    }
}

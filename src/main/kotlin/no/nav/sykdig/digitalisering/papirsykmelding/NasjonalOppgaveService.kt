package no.nav.sykdig.digitalisering.papirsykmelding

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.LoggingMeta
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.norskHelsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirManuellOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.PapirSmRegistering
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.SmRegistreringManuell
import no.nav.sykdig.digitalisering.papirsykmelding.db.NasjonalOppgaveRepository
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.securelog
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class NasjonalOppgaveService(
    private val nasjonalOppgaveRepository: NasjonalOppgaveRepository,
    private val personService: PersonService,
    private val sykmelderService: SykmelderService

) {
    val log = applog()
    val securelog = securelog()

    fun lagreOppgave(papirManuellOppgave: PapirManuellOppgave): NasjonalManuellOppgaveDAO {
        val eksisterendeOppgave = nasjonalOppgaveRepository.findBySykmeldingId(papirManuellOppgave.sykmeldingId)
        if (eksisterendeOppgave.isPresent) {
            return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, eksisterendeOppgave.get().id))
        }
        return nasjonalOppgaveRepository.save(mapToDao(papirManuellOppgave, null))
    }


    // usikker på hva som skal returneres her
    suspend fun sendPapirsykmelding(smRegistreringManuell: SmRegistreringManuell, navEnhet: String, callId: String, oppgaveId: String): ResponseEntity<String> {
        val oppgave = nasjonalOppgaveRepository.findByOppgaveId(oppgaveId)
        if (!oppgave.isPresent) return ResponseEntity(HttpStatus.NOT_FOUND) // TODO: bedre error handeling

        val loggingMeta = LoggingMeta(
            mottakId = oppgave.get().sykmeldingId,
            dokumentInfoId = oppgave.get().dokumentInfoId,
            msgId = oppgave.get().sykmeldingId,
            sykmeldingId = oppgave.get().sykmeldingId,
            journalpostId = oppgave.get().journalpostId,
        )

        val sykmelderHpr = smRegistreringManuell.behandler.hpr
        if (sykmelderHpr.isNullOrEmpty()) {
            log.error("HPR-nummer mangler {}", StructuredArguments.fields(loggingMeta))
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }

        log.info("Henter sykmelder fra HPR og PDL")
        val sykmelder = sykmelderService.hentSykmelder(
                sykmelderHpr,
                callId,
            )

        log.info("Henter pasient fra PDL {} ", loggingMeta)
        val pasient =
            personService.getPerson(
                id = smRegistreringManuell.pasientFnr,
                callId = callId,
            )

        val tssId =




        // logging meta sykmeldingId, dokumentInfoId, journalpostId

        // sender med et isUpdate - som sjekker om saksbehandler har superuseraccess. men dette brukes kun i endre, som ikke har blitt brukt

        // sjekker om saksbehandler har tilgang, men jeg tror dette kan gjøres gjennom obo token i controlleren

        // val sykmelderHpr
        // val sykmelder = personServise.hentPerson(sykmeldderHpr)   -- callId er en randomUUID - spørre seg om kanskje ha callId som sykmeldingId












    }

    fun mapToDao(
        papirManuellOppgave: PapirManuellOppgave,
        existingId: UUID?,
    ): NasjonalManuellOppgaveDAO {
        val mapper = jacksonObjectMapper()
        mapper.registerModules(JavaTimeModule())

        val nasjonalManuellOppgaveDAO =
            NasjonalManuellOppgaveDAO(
                sykmeldingId = papirManuellOppgave.sykmeldingId,
                journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                fnr = papirManuellOppgave.fnr,
                aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet?.toLocalDateTime(),
                oppgaveId = papirManuellOppgave.oppgaveid,
                ferdigstilt = false,
                papirSmRegistrering =
                    PapirSmRegistering(
                        journalpostId = papirManuellOppgave.papirSmRegistering.journalpostId,
                        oppgaveId = papirManuellOppgave.papirSmRegistering.oppgaveId,
                        fnr = papirManuellOppgave.papirSmRegistering.fnr,
                        aktorId = papirManuellOppgave.papirSmRegistering.aktorId,
                        dokumentInfoId = papirManuellOppgave.papirSmRegistering.dokumentInfoId,
                        datoOpprettet = papirManuellOppgave.papirSmRegistering.datoOpprettet,
                        sykmeldingId = papirManuellOppgave.papirSmRegistering.sykmeldingId,
                        syketilfelleStartDato = papirManuellOppgave.papirSmRegistering.syketilfelleStartDato,
                        arbeidsgiver = papirManuellOppgave.papirSmRegistering.arbeidsgiver,
                        medisinskVurdering = papirManuellOppgave.papirSmRegistering.medisinskVurdering,
                        skjermesForPasient = papirManuellOppgave.papirSmRegistering.skjermesForPasient,
                        perioder = papirManuellOppgave.papirSmRegistering.perioder,
                        prognose = papirManuellOppgave.papirSmRegistering.prognose,
                        utdypendeOpplysninger = papirManuellOppgave.papirSmRegistering.utdypendeOpplysninger,
                        tiltakNAV = papirManuellOppgave.papirSmRegistering.tiltakNAV,
                        tiltakArbeidsplassen = papirManuellOppgave.papirSmRegistering.tiltakArbeidsplassen,
                        andreTiltak = papirManuellOppgave.papirSmRegistering.andreTiltak,
                        meldingTilNAV = papirManuellOppgave.papirSmRegistering.meldingTilNAV,
                        meldingTilArbeidsgiver = papirManuellOppgave.papirSmRegistering.meldingTilArbeidsgiver,
                        kontaktMedPasient = papirManuellOppgave.papirSmRegistering.kontaktMedPasient,
                        behandletTidspunkt = papirManuellOppgave.papirSmRegistering.behandletTidspunkt,
                        behandler = papirManuellOppgave.papirSmRegistering.behandler,
                    ),
                utfall = null,
                ferdigstiltAv = null,
                datoFerdigstilt = null,
                avvisningsgrunn = null,
            )

        if (existingId != null) {
            nasjonalManuellOppgaveDAO.apply {
                id = existingId
            }
        }

        return nasjonalManuellOppgaveDAO
    }
}

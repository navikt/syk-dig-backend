package no.nav.sykdig.utenlandsk.services

import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.shared.LoggingMeta
import no.nav.sykdig.shared.applog
import no.nav.sykdig.dokarkiv.DokarkivClient
import no.nav.sykdig.shared.Periode
import no.nav.sykdig.nasjonal.mapping.NasjonalSykmeldingMapper
import no.nav.sykdig.nasjonal.models.FerdigstillRegistrering
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.saf.SafJournalpostService
import no.nav.sykdig.saf.graphql.SafJournalpost
import no.nav.sykdig.saf.graphql.TEMA_SYKEPENGER
import no.nav.sykdig.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.saf.graphql.Type
import no.nav.sykdig.utenlandsk.db.JournalpostSykmeldingRepository
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.generated.types.JournalpostStatusEnum
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.shared.securelog
import org.springframework.stereotype.Service

@Service
class JournalpostService(
    private val sykmeldingService: SykmeldingService,
    private val personService: PersonService,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val journalpostSykmeldingRepository: JournalpostSykmeldingRepository,
    private val metricRegister: MetricRegister,
    private val safJournalpostService: SafJournalpostService,
    private val dokarkivClient: DokarkivClient,
    private val nasjonalSykmeldingMapper: NasjonalSykmeldingMapper,
) {
    companion object {
        private val securelog = securelog()
        private val log = applog()
    }

    fun createSykmeldingFromJournalpost(
        journalpost: SafJournalpost,
        journalpostId: String,
        isNorsk: Boolean,
    ): JournalpostResult {
        if (isWrongTema(journalpost)) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }
        sykDigOppgaveService.ferdigstillExistingJournalfoeringsoppgave(journalpostId, journalpost)
        if (isNorsk) {
            sykmeldingService.createSykmelding(journalpostId, journalpost.tema!!)
            journalpostSykmeldingRepository.insertJournalpostId(journalpostId)
            securelog.info(
                "oppretter sykmelding fra journalpost {} {} {}",
                kv("journalpostId", journalpostId),
                kv("kanal", journalpost.kanal),
                kv("type", "norsk papirsykmelding"),
            )

            metricRegister.incrementNewSykmelding("norsk", journalpost.kanal)
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.OPPRETTET,
            )
        }
        val fnrEllerAktorId =
            getFnrEllerAktorId(journalpost)
                ?: return JournalpostStatus(
                    journalpostId = journalpostId,
                    status = JournalpostStatusEnum.MANGLER_FNR,
                )
        val fnr = personService.getPerson(fnrEllerAktorId, journalpostId).fnr
        val aktorId = personService.getPerson(fnrEllerAktorId, journalpostId).aktorId
        val oppgaveId = sykDigOppgaveService.opprettOgLagreOppgave(journalpost, journalpostId, fnr, aktorId, nasjonalSykmeldingMapper.getNavEmail())

        securelog.info(
            "oppretter sykmelding fra journalpost {} {} {} {}",
            kv("journalpostId", journalpostId),
            kv("kanal", journalpost.kanal),
            kv("type", "utenlandsk sykmelding"),
            kv("fnr", fnr),
        )

        metricRegister.incrementNewSykmelding("utenlandsk", journalpost.kanal)

        journalpostSykmeldingRepository.insertJournalpostId(journalpostId)
        return JournalpostStatus(
            journalpostId = journalpostId,
            status = JournalpostStatusEnum.OPPRETTET,
            oppgaveId = oppgaveId,
        )
    }

    fun getJournalpostResult(
        journalpost: SafJournalpost,
        journalpostId: String,
    ): JournalpostResult {
        val fnrEllerAktorId =
            getFnrEllerAktorId(journalpost)
                ?: return JournalpostStatus(
                    journalpostId = journalpostId,
                    status = JournalpostStatusEnum.MANGLER_FNR,
                )

        val fnr = personService.getPerson(fnrEllerAktorId, journalpostId).fnr
        securelog.info(
            "Henter journalpost {} {} {}",
            kv("journalpostId", journalpostId),
            kv("kanal", journalpost.kanal),
            kv("fnr", fnr),
        )
        if (isWrongTema(journalpost)) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }

        return Journalpost(
            journalpostId,
            journalpost.journalstatus?.name ?: "MANGLER_STATUS",
            dokumenter =
                journalpost.dokumenter.map {
                    Document(it.tittel ?: "Mangler Tittel", it.dokumentInfoId)
                },
            fnr = fnr,
        )
    }
    suspend fun ferdigstillNasjonalJournalpost(
        ferdigstillRegistrering: FerdigstillRegistrering,
        perioder: List<Periode>?,
        loggingMeta: LoggingMeta,
    ) {
        if (
            safJournalpostService.erIkkeJournalfort(journalpostId = ferdigstillRegistrering.journalpostId)
        ) {
            log.info("ferdigstiller i dokarkiv {}", StructuredArguments.fields(loggingMeta))
            dokarkivClient.oppdaterOgFerdigstillNasjonalJournalpost(
                journalpostId = ferdigstillRegistrering.journalpostId,
                dokumentInfoId = ferdigstillRegistrering.dokumentInfoId,
                pasientFnr = ferdigstillRegistrering.pasientFnr,
                sykmeldingId = ferdigstillRegistrering.sykmeldingId,
                sykmelder = ferdigstillRegistrering.sykmelder,
                navEnhet = ferdigstillRegistrering.navEnhet,
                avvist = ferdigstillRegistrering.avvist,
                perioder = perioder
            )
        } else {
            log.info(
                "Hopper over oppdaterOgFerdigstillJournalpost, " +
                        "journalpostId ${ferdigstillRegistrering.journalpostId} er allerede journalført",
            )
        }
    }


    fun isSykmeldingCreated(id: String): Boolean {
        return journalpostSykmeldingRepository.getJournalpostSykmelding(id) != null
    }

    private fun isWrongTema(journalpost: SafJournalpost): Boolean {
        return journalpost.tema != TEMA_SYKMELDING && journalpost.tema != TEMA_SYKEPENGER
    }

    private fun getFnrEllerAktorId(journalpost: SafJournalpost): String? {
        return when (journalpost.bruker?.type) {
            Type.ORGNR -> null
            else -> journalpost.bruker?.id
        }
    }
}

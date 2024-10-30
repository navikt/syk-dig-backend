package no.nav.sykdig.digitalisering.sykmelding.service

import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalManuellOppgaveDAO
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKEPENGER
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.digitalisering.saf.graphql.Type
import no.nav.sykdig.digitalisering.sykmelding.db.JournalpostSykmeldingRepository
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.generated.types.JournalpostStatusEnum
import no.nav.sykdig.metrics.MetricRegister
import no.nav.sykdig.securelog
import org.springframework.stereotype.Service

@Service
class JournalpostService(
    private val sykmeldingService: SykmeldingService,
    private val personService: PersonService,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val journalpostSykmeldingRepository: JournalpostSykmeldingRepository,
    private val metricRegister: MetricRegister,
) {
    companion object {
        private val securelog = securelog()
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
        val fnr = personService.hentPerson(fnrEllerAktorId, journalpostId).fnr
        val aktorId = personService.hentPerson(fnrEllerAktorId, journalpostId).aktorId
        val oppgaveId = sykDigOppgaveService.opprettOgLagreOppgave(journalpost, journalpostId, fnr, aktorId)

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

        val fnr = personService.hentPerson(fnrEllerAktorId, journalpostId).fnr
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

    fun ferdigstillAvvistOppgave(
        oppgave: NasjonalManuellOppgaveDAO,
        bruker: String,
        navEnhet: String,
        navEpost: String,
        avvisingsgrunn: String,
    ) {
        val oppgave = sykDigOppgaveService.getOppgave(oppgave.oppgaveId.toString())
        val sykmeldt =
            personService.hentPerson(
                id = oppgave.fnr,
                callId = oppgave.sykmeldingId.toString(),
            )
        val avvistGrunn = enumValues<Avvisingsgrunn>().find { it.name.equals(avvisingsgrunn, ignoreCase = true) }
        if (avvistGrunn != null) {
            sykDigOppgaveService.ferdigstillAvvistOppgave(
                oppgave = oppgave,
                navEpost = "",
                enhetId = navEnhet,
                sykmeldt = sykmeldt,
                avvisningsgrunn = avvistGrunn,
                avvisningsgrunnAnnet = null,
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

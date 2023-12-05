package no.nav.sykdig.digitalisering.sykmelding.service

import no.nav.sykdig.digitalisering.SykDigOppgaveService
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_IM
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_NETS
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.digitalisering.saf.graphql.Type
import no.nav.sykdig.digitalisering.sykmelding.db.JournalpostSykmeldingRepository
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.generated.types.JournalpostStatusEnum
import org.springframework.stereotype.Service

@Service
class JournalpostService(
    private val sykmeldingService: SykmeldingService,
    private val personService: PersonService,
    private val sykDigOppgaveService: SykDigOppgaveService,
    private val journalpostSykmeldingRepository: JournalpostSykmeldingRepository,
) {

    fun createSykmeldingFromJournalpost(journalpost: SafJournalpost, journalpostId: String, isNorsk: Boolean): JournalpostResult {
        if (isWrongTema(journalpost)) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }

        when (isNorsk) {
            true -> {
                if (isWrongChannel(journalpost)) {
                    return JournalpostStatus(
                        journalpostId = journalpostId,
                        status = JournalpostStatusEnum.FEIL_KANAL,
                    )
                }
                sykmeldingService.createSykmelding(journalpostId, journalpost.tema!!)
            }
            false -> {
                val fnrEllerAktorId = getFnrEllerAktorId(journalpost)
                    ?: return JournalpostStatus(
                        journalpostId = journalpostId,
                        status = JournalpostStatusEnum.MANGLER_FNR,
                    )

                val fnr = personService.hentPerson(fnrEllerAktorId, journalpostId).fnr

                sykDigOppgaveService.opprettOgLagreOppgave(journalpost, journalpostId, fnr)
            }
        }

        journalpostSykmeldingRepository.insertJournalpostId(journalpostId)

        return JournalpostStatus(
            journalpostId = journalpostId,
            status = JournalpostStatusEnum.OPPRETTET,
        )
    }

    fun getJournalpostResult(journalpost: SafJournalpost, journalpostId: String): JournalpostResult {
        val fnrEllerAktorId = getFnrEllerAktorId(journalpost)
            ?: return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.MANGLER_FNR,
            )

        val fnr = personService.hentPerson(fnrEllerAktorId, journalpostId).fnr
        if (isWrongChannel(journalpost)) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_KANAL,
            )
        }
        if (isWrongTema(journalpost)) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }
        return Journalpost(
            journalpostId,
            journalpost.journalstatus?.name ?: "MANGLER_STATUS",
            dokumenter = journalpost.dokumenter.map {
                Document(it.tittel ?: "Mangler Tittel", it.dokumentInfoId)
            },
            fnr = fnr,
        )
    }

    fun isSykmeldingCreated(id: String): Boolean {
        return journalpostSykmeldingRepository.getJournalpostSykmelding(id) != null
    }

    private fun isWrongChannel(journalpost: SafJournalpost): Boolean {
        return !listOf(CHANNEL_SCAN_IM, CHANNEL_SCAN_NETS).contains(journalpost.kanal)
    }

    private fun isWrongTema(journalpost: SafJournalpost): Boolean {
        return journalpost.tema != TEMA_SYKMELDING
    }

    private fun getFnrEllerAktorId(journalpost: SafJournalpost): String? {
        return when (journalpost.bruker?.type) {
            Type.ORGNR -> null
            else -> journalpost.bruker?.id
        }
    }
}

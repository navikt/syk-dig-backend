package no.nav.sykdig.digitalisering.sykmelding.service

import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_IM
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_NETS
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.digitalisering.saf.graphql.Type
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
    private val journalpostOppgaveService: JournalpostOppgaveService,
) {

    fun createSykmeldingFromJournalpost(journalpost: SafQueryJournalpost, journalpostId: String, norsk: Boolean): JournalpostResult {
        if (isWrongTema(journalpost)) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }

        if (norsk) {
            if (isWrongChannel(journalpost)) {
                return JournalpostStatus(
                    journalpostId = journalpostId,
                    status = JournalpostStatusEnum.FEIL_KANAL,
                )
            }
        } else {
            val fnrEllerAktorId = getFnrEllerAktorId(journalpost)
                ?: return JournalpostStatus(
                    journalpostId = journalpostId,
                    status = JournalpostStatusEnum.MANGLER_FNR,
                )

            val fnr = personService.hentPerson(fnrEllerAktorId, journalpostId).fnr
            journalpostOppgaveService.opprettOgLagreOppgave(journalpost, journalpostId, fnr)
        }
        sykmeldingService.createSykmelding(journalpostId, journalpost.journalpost?.tema!!)

        return JournalpostStatus(
            journalpostId = journalpostId,
            status = JournalpostStatusEnum.OPPRETTET,
        )
    }

    fun getJournalpostResult(journalpost: SafQueryJournalpost, journalpostId: String): JournalpostResult {
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
            journalpost.journalpost?.journalstatus?.name ?: "MANGLER_STATUS",
            dokumenter = journalpost.journalpost?.dokumenter?.map {
                Document(it.tittel ?: "Mangler Tittel", it.dokumentInfoId)
            } ?: emptyList(),
            fnr = fnr,
        )
    }

    private fun isWrongChannel(journalpost: SafQueryJournalpost): Boolean {
        return !listOf(CHANNEL_SCAN_IM, CHANNEL_SCAN_NETS).contains(journalpost.journalpost?.kanal)
    }

    private fun isWrongTema(journalpost: SafQueryJournalpost): Boolean {
        return journalpost.journalpost?.tema != TEMA_SYKMELDING
    }

    private fun getFnrEllerAktorId(journalpost: SafQueryJournalpost): String? {
        return when (journalpost.journalpost?.bruker?.type) {
            Type.ORGNR -> null
            else -> journalpost.journalpost?.bruker?.id
        }
    }
}

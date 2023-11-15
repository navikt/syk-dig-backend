package no.nav.sykdig.digitalisering.sykmelding.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.digitalisering.dokarkiv.BrukerIdType
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_IM
import no.nav.sykdig.digitalisering.saf.graphql.CHANNEL_SCAN_NETS
import no.nav.sykdig.digitalisering.saf.graphql.TEMA_SYKMELDING
import no.nav.sykdig.digitalisering.sykmelding.service.SykmeldingService
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Document
import no.nav.sykdig.generated.types.Journalpost
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.generated.types.JournalpostStatusEnum
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize

@DgsComponent
class JournalpostDataFetcher(
    private val safGraphQlClient: SafJournalpostGraphQlClient,
    private val personService: PersonService,
    private val sykmeldingService: SykmeldingService
) {

    @PostAuthorize("@oppgaveSecurityService.hasAccessToJournalpost(returnObject)")
    @DgsQuery(field = DgsConstants.QUERY.Journalpost)
    fun getJournalpostById(
        @InputArgument id: String,
    ): JournalpostResult {
        val journalpost = safGraphQlClient.getJournalpost(id)
        val fnrEllerAktorId = when (journalpost.journalpost?.bruker?.idType) {
            BrukerIdType.ORGNR.toString() -> null
            else -> journalpost.journalpost?.bruker?.id
        }

        if (fnrEllerAktorId == null) {
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.MANGLER_FNR,
            )
        }

        if (journalpost.journalpost?.tema != TEMA_SYKMELDING) {
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }

        if (!listOf(CHANNEL_SCAN_IM, CHANNEL_SCAN_NETS).contains(journalpost.journalpost.kanal)) {
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.FEIL_KANAL,
            )
        }

        val fnr = personService.hentPerson(fnrEllerAktorId, id).fnr
        return Journalpost(
            id,
            journalpost.journalpost.journalstatus?.name ?: "MANGLER_STATUS",
            dokumenter = journalpost.journalpost.dokumenter?.map {
                Document(it.tittel ?: "Mangler Tittel", it.dokumentInfoId)
            } ?: emptyList(),
            fnr = fnr,
        )
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToJournalpostId(#journalpostId)")
    @DgsMutation(field = DgsConstants.MUTATION.SykmeldingFraJournalpost)
    fun createSykmelding(
        @InputArgument journalpostId: String,
    ): JournalpostResult {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)

        if (journalpost.journalpost?.tema != TEMA_SYKMELDING) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_TEMA,
            )
        }

        if (!listOf(CHANNEL_SCAN_IM, CHANNEL_SCAN_NETS).contains(journalpost.journalpost.kanal)) {
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.FEIL_KANAL,
            )
        }

        sykmeldingService.createSykmelding(journalpostId, journalpost.journalpost.tema)

        return JournalpostStatus(
            journalpostId = journalpostId,
            status = JournalpostStatusEnum.OPPRETTET,
        )
    }
}

package no.nav.sykdig.digitalisering.sykmelding.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.sykmelding.service.JournalpostService
import no.nav.sykdig.digitalisering.sykmelding.service.SykmeldingService
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.generated.types.JournalpostStatusEnum
import no.nav.sykdig.logger
import no.nav.sykdig.objectMapper
import no.nav.sykdig.securelog
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize

@DgsComponent
class JournalpostDataFetcher(
    private val safGraphQlClient: SafJournalpostGraphQlClient,
    private val sykmeldingService: SykmeldingService,
    private val journalpostService: JournalpostService,
) {
    companion object {
        private val securelog = securelog()
        private val log = logger()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToJournalpost(returnObject)")
    @DgsQuery(field = DgsConstants.QUERY.Journalpost)
    fun getJournalpostById(
        @InputArgument id: String,
    ): JournalpostResult {
        val journalpost = safGraphQlClient.getJournalpost(id).journalpost ?: return JournalpostStatus(
            journalpostId = id,
            status = JournalpostStatusEnum.MANGLENDE_JOURNALPOST,
        )

        securelog.info("journalpost from saf: ${objectMapper.writeValueAsString(journalpost)}")

        if (journalpostService.isSykmeldingCreated(id)) {
            log.info("Sykmelding already created for journalpost id $id")
            return JournalpostStatus(
                journalpostId = id,
                status = JournalpostStatusEnum.OPPRETTET,
            )
        }

        return journalpostService.getJournalpostResult(journalpost, id)
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToJournalpostId(#journalpostId)")
    @DgsMutation(field = DgsConstants.MUTATION.SykmeldingFraJournalpost)
    fun createSykmelding(
        @InputArgument journalpostId: String,
        @InputArgument norsk: Boolean,
    ): JournalpostResult {
        if (journalpostService.isSykmeldingCreated(journalpostId)) {
            log.info("Sykmelding already created for journalpost id $journalpostId")
            return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.OPPRETTET,
            )
        }
        val journalpost = safGraphQlClient.getJournalpost(journalpostId).journalpost
            ?: return JournalpostStatus(
                journalpostId = journalpostId,
                status = JournalpostStatusEnum.MANGLENDE_JOURNALPOST,
            )
        return journalpostService.createSykmeldingFromJournalpost(journalpost, journalpostId, isNorsk = norsk)
    }
}

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
        val journalpost = safGraphQlClient.getJournalpost(id)
        securelog.info("journalpost from saf: ${objectMapper.writeValueAsString(journalpost)}")

        if (sykmeldingService.isSykmeldingCreated(id)) {
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
        norsk: Boolean,
    ): JournalpostResult {
        val journalpost = safGraphQlClient.getJournalpost(journalpostId)
        return journalpostService.createSykmeldingFromJournalpost(journalpost, journalpostId, norsk)
    }
}

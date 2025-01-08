package no.nav.sykdig.utenlandsk.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.felles.applog
import no.nav.sykdig.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.utenlandsk.services.JournalpostService
import no.nav.sykdig.utenlandsk.services.SykmeldingService
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.JournalpostResult
import no.nav.sykdig.generated.types.JournalpostStatus
import no.nav.sykdig.generated.types.JournalpostStatusEnum
import no.nav.sykdig.felles.objectMapper
import no.nav.sykdig.felles.securelog
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
        private val log = applog()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToJournalpost(returnObject)")
    @DgsQuery(field = DgsConstants.QUERY.Journalpost)
    fun getJournalpostById(
        @InputArgument id: String,
    ): JournalpostResult {
        val trimedJournalpostId = id.trim()
        val journalpost =
            safGraphQlClient.getJournalpost(trimedJournalpostId).journalpost ?: return JournalpostStatus(
                journalpostId = trimedJournalpostId,
                status = JournalpostStatusEnum.MANGLENDE_JOURNALPOST,
            )
        securelog.info("journalpost from saf: ${objectMapper.writeValueAsString(journalpost)}")

        if (journalpostService.isSykmeldingCreated(trimedJournalpostId)) {
            log.info("Sykmelding already created for journalpost id $trimedJournalpostId")
            return JournalpostStatus(
                journalpostId = trimedJournalpostId,
                status = JournalpostStatusEnum.OPPRETTET,
            )
        }

        return journalpostService.getJournalpostResult(journalpost, trimedJournalpostId)
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToJournalpostId(#journalpostId)")
    @DgsMutation(field = DgsConstants.MUTATION.SykmeldingFraJournalpost)
    fun createSykmelding(
        @InputArgument journalpostId: String,
        @InputArgument norsk: Boolean,
    ): JournalpostResult {
        val trimedJournalpostId = journalpostId.trim()
        if (journalpostService.isSykmeldingCreated(trimedJournalpostId)) {
            log.info("Sykmelding already created for journalpost id $trimedJournalpostId")
            return JournalpostStatus(
                journalpostId = trimedJournalpostId,
                status = JournalpostStatusEnum.OPPRETTET,
            )
        }
        val journalpost =
            safGraphQlClient.getJournalpost(trimedJournalpostId).journalpost
                ?: return JournalpostStatus(
                    journalpostId = trimedJournalpostId,
                    status = JournalpostStatusEnum.MANGLENDE_JOURNALPOST,
                )
        return journalpostService.createSykmeldingFromJournalpost(journalpost, trimedJournalpostId, isNorsk = norsk)
    }
}

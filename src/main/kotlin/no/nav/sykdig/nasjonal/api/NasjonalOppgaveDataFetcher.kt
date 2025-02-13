package no.nav.sykdig.nasjonal.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.papirsykmelding.mapToNasjonalOppgave
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.NasjonalOppgaveResult
import no.nav.sykdig.generated.types.NasjonalOppgaveStatus
import no.nav.sykdig.generated.types.NasjonalOppgaveStatusEnum
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.securelog
import org.springframework.security.access.prepost.PostAuthorize


@DgsComponent
class NasjonalOppgaveDataFetcher(
    private val nasjonalOppgaveService: NasjonalOppgaveService,
) {

    companion object {
        val log = applog()
        val securelog = securelog()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, #authorization, '/dgs/nasjonal/oppgave/{oppgaveId}')")
    @DgsQuery(field = DgsConstants.QUERY.NasjonalOppgave)
    fun getNasjonalOppgave(oppgaveId: String, authorization: String, dfe: DataFetchingEnvironment): NasjonalOppgaveResult? {
        val oppgave = nasjonalOppgaveService.getOppgave(oppgaveId, authorization)
        if (oppgave != null) {
            if (oppgave.ferdigstilt) {
                log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
                return NasjonalOppgaveStatus(oppgaveId, NasjonalOppgaveStatusEnum.FERDIGSTILT)
            }
            requireNotNull(oppgave.fnr)
            // TODO remove bangs after migration
            val sykmelderFnr = oppgave.papirSmRegistrering!!.behandler?.fnr
            requireNotNull(sykmelderFnr)
            return mapToNasjonalOppgave(oppgave)
        }

        return null
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalSykmelding(#sykmeldingId, #authorization, '/dgs/nasjonal/sykmelding/{sykmeldingId}/ferdigstilt')")
    @DgsQuery(field = DgsConstants.QUERY.NasjonalFerdigstiltOppgave)
    fun getFerdigstiltNasjonalOppgave(sykmeldingId: String, authorization: String, dfe: DataFetchingEnvironment): NasjonalOppgaveResult? {
        val oppgave = nasjonalOppgaveService.getOppgaveBySykmeldingIdSmreg(sykmeldingId, authorization)
        if (oppgave != null) {
            if (!oppgave.ferdigstilt) {
                log.info("Oppgave med sykmeldingId $sykmeldingId er ikke ferdigstilt")
                return NasjonalOppgaveStatus(oppgave.oppgaveId.toString(), NasjonalOppgaveStatusEnum.IKKE_FERDIGSTILT)
            }
            requireNotNull(oppgave.fnr)
            // TODO remove bangs after migration
            val sykmelderFnr = oppgave.papirSmRegistrering!!.behandler?.fnr
            requireNotNull(sykmelderFnr)
            return mapToNasjonalOppgave(oppgave)
        }

        return NasjonalOppgaveStatus(oppgave?.oppgaveId.toString(), NasjonalOppgaveStatusEnum.FINNES_IKKE)
    }

}
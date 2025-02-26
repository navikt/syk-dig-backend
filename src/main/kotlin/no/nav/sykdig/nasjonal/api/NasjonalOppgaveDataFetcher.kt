package no.nav.sykdig.nasjonal.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.papirsykmelding.mapToNasjonalOppgave
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.NasjonalOppgaveResult
import no.nav.sykdig.generated.types.NasjonalOppgaveStatus
import no.nav.sykdig.generated.types.NasjonalOppgaveStatusEnum
import no.nav.sykdig.nasjonal.services.NasjonalDbService
import no.nav.sykdig.generated.types.NasjonalSykmeldingResult
import no.nav.sykdig.generated.types.NasjonalSykmeldingStatus
import no.nav.sykdig.generated.types.NasjonalOppdatertSykmeldingStatusEnum
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.applog
import org.springframework.security.access.prepost.PostAuthorize


@DgsComponent
class NasjonalOppgaveDataFetcher(
    private val nasjonalDbService: NasjonalDbService,
) {

    companion object {
        val log = applog()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/dgs/nasjonal/oppgave/{oppgaveId}')")
    @DgsQuery(field = DgsConstants.QUERY.NasjonalOppgave)
    fun getNasjonalOppgave(@InputArgument oppgaveId: String, dfe: DataFetchingEnvironment): NasjonalOppgaveResult? {
        log.info("Henter najsonal oppgave med id $oppgaveId")
        val oppgave = nasjonalDbService.getOppgave(oppgaveId)
        if (oppgave != null) {
            if (oppgave.ferdigstilt) {
                log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
                return NasjonalOppgaveStatus(oppgaveId, NasjonalOppgaveStatusEnum.FERDIGSTILT)
            }
            requireNotNull(oppgave.fnr)
            val sykmelderFnr = oppgave.papirSmRegistrering.behandler?.fnr
            requireNotNull(sykmelderFnr)
            return mapToNasjonalOppgave(oppgave)
        }
        return NasjonalOppgaveStatus(oppgaveId, NasjonalOppgaveStatusEnum.FINNES_IKKE)
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalSykmelding(#sykmeldingId, '/dgs/nasjonal/sykmelding/{sykmeldingId}/ferdigstilt')")
    @DgsQuery(field = DgsConstants.QUERY.NasjonalFerdigstiltOppgave)
    fun getFerdigstiltNasjonalOppgave(@InputArgument sykmeldingId: String, dfe: DataFetchingEnvironment): NasjonalSykmeldingResult? {
        val oppgave = nasjonalDbService.getOppgaveBySykmeldingIdSmreg(sykmeldingId)
        if (oppgave != null) {
            if (!oppgave.ferdigstilt) {
                log.info("Oppgave med sykmeldingId $sykmeldingId er ikke ferdigstilt")
                return NasjonalSykmeldingStatus(sykmeldingId, NasjonalOppdatertSykmeldingStatusEnum.IKKE_FERDIGSTILT)
            }
            requireNotNull(oppgave.fnr)
            val sykmelderFnr = oppgave.papirSmRegistrering.behandler?.fnr
            requireNotNull(sykmelderFnr)
            return mapToNasjonalOppgave(oppgave)
        }
        return NasjonalSykmeldingStatus(sykmeldingId, NasjonalOppdatertSykmeldingStatusEnum.FINNES_IKKE)
    }
}
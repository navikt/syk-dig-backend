package no.nav.sykdig.nasjonal.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.papirsykmelding.mapToNasjonalOppgave
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.NasjonalOppgaveResult
import no.nav.sykdig.generated.types.NasjonalOppgaveStatus
import no.nav.sykdig.generated.types.NasjonalOppgaveStatusEnum
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.nasjonal.services.NasjonalSykmeldingService
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.securelog
import org.springframework.security.access.prepost.PostAuthorize


@DgsComponent
class NasjonalOppgaveDataFetcher(
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val sykmelderService: SykmelderService,
    private val personService: PersonService,
    private val nasjonalSykmeldingService: NasjonalSykmeldingService,
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
            val sykmelderFnr = oppgave.papirSmRegistrering.behandler?.fnr
            requireNotNull(sykmelderFnr)
            return mapToNasjonalOppgave(oppgave)
        }

        return null
    }


}
package no.nav.sykdig.digitalisering.papirsykmelding.api

import com.netflix.graphql.dgs.DgsComponent
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalSykmeldingService
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.generated.types.NasjonalOppgave
import no.nav.sykdig.generated.types.NasjonalOppgaveResult
import no.nav.sykdig.generated.types.NasjonalOppgaveStatus
import no.nav.sykdig.securelog


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
//TODO find out what NasjonalSykmeldingResult is and its parameters
    fun getNasjonalOppgave(oppgaveId: String, authorization: String, dfe: DataFetchingEnvironment): NasjonalOppgaveResult? {
        val oppgave = nasjonalOppgaveService.getOppgave(oppgaveId, authorization)
        if (oppgave != null) {
            val nasjonalOppgave = mapToNasjonalSykmelding(oppgave)
         if(oppgave.ferdigstilt) {
             log.info("Oppgave med id $oppgaveId er allerede ferdigstilt")
             return NasjonalOppgaveStatus(oppgaveId, mapToNasjonalSykmelding, documents)
         }
            requireNotNull(oppgave.fnr)
            val sykmelderFnr = oppgave.papirSmRegistrering.behandler?.fnr
            requireNotNull(sykmelderFnr)
            return NasjonalOppgave
        }
    }





}
package no.nav.sykdig.digitalisering.papirsykmelding.api

import com.netflix.graphql.dgs.DgsComponent
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.helsenett.SykmelderService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalOppgaveService
import no.nav.sykdig.digitalisering.papirsykmelding.NasjonalSykmeldingService
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.securelog
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






}
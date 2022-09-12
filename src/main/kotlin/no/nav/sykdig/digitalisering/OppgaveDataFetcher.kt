package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.Person
import no.nav.sykdig.logger
import no.nav.sykdig.tilgangskontroll.SyfoTilgangskontrollOboClient

@DgsComponent
class OppgaveDataFetcher(
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
    private val oppgaveRepository: OppgaveRepository
) {
    private val log = logger()

    @DgsQuery(field = "oppgave")

    fun getOppgave(@InputArgument oppgaveId: String): Digitaliseringsoppgave? {

        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        // hent navn og adresse fra PDL
        // hent PDF fra SAF

       // utvid format med SykmeldingUnderArbeid
        return if (oppgave != null) {
            if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(oppgave.fnr)) {
                throw RuntimeException("Ikke tilgang")
            }
            Digitaliseringsoppgave(
                oppgaveId = oppgave.oppgaveId,
                sykmeldingId = oppgave.sykmeldingId.toString(),
                person = Person(
                    fnr = oppgave.fnr,
                    navn = null,
                    adresser = emptyList()
                ),
                pdf = null
            )
        } else {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            null
        }
    }
}

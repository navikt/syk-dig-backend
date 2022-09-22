package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.Person
import no.nav.sykdig.generated.types.SykmeldingUnderArbeid
import no.nav.sykdig.generated.types.SykmeldingsType
import no.nav.sykdig.logger

@DgsComponent
class OppgaveDataFetcher(
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
    private val oppgaveRepository: OppgaveRepository,
    private val personService: PersonService
) {
    private val log = logger()

    @DgsQuery(field = "oppgave")
    fun getOppgave(@InputArgument oppgaveId: String): Digitaliseringsoppgave {

        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave != null) {
            if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(oppgave.fnr)) {
                log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
                throw RuntimeException("Innlogget bruker har ikke tilgang")
            }
            try {
                val person = personService.hentPerson(fnr = oppgave.fnr, sykmeldingId = oppgave.sykmeldingId.toString())
                return Digitaliseringsoppgave(
                    oppgaveId = oppgave.oppgaveId,
                    person = Person(
                        fnr = person.fnr,
                        navn = person.navn.toFormattedNameString(),
                    ),
                    type = if (oppgave.type == "UTLAND") {
                        SykmeldingsType.UTENLANDS
                    } else {
                        SykmeldingsType.INNENLANDS
                    },
                    values = oppgave.sykmelding?.let {
                        SykmeldingUnderArbeid(
                            personNrPasient = it.personNrPasient
                        )
                    } ?: SykmeldingUnderArbeid()
                )
            } catch (e: Exception) {
                log.error("Noe gikk galt ved henting av oppgave med id $oppgaveId")
                throw RuntimeException("Noe gikk galt ved henting av oppgave")
            }
        } else {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw RuntimeException("Fant ikke oppgave")
        }
    }
}

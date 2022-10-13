package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.exceptions.IkkeTilgangException
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.DiagnoseValue
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.OppgaveValues
import no.nav.sykdig.generated.types.Person
import no.nav.sykdig.generated.types.SykmeldingsType
import no.nav.sykdig.logger
import no.nav.sykdig.model.SykmeldingUnderArbeid

@DgsComponent
class OppgaveDataFetcher(
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
    private val oppgaveRepository: OppgaveRepository,
    private val personService: PersonService,
) {
    private val log = logger()

    @DgsQuery(field = "oppgave")
    fun getOppgave(@InputArgument oppgaveId: String): Digitaliseringsoppgave {

        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        if (oppgave != null) {
            if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(oppgave.fnr)) {
                log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
                throw IkkeTilgangException("Innlogget bruker har ikke tilgang")
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
                    values = oppgave.sykmelding?.mapToOppgaveValues() ?: OppgaveValues()
                )
            } catch (e: Exception) {
                log.error("Noe gikk galt ved henting av oppgave med id $oppgaveId")
                throw RuntimeException("Noe gikk galt ved henting av oppgave")
            }
        } else {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            throw DgsEntityNotFoundException("Fant ikke oppgave")
        }
    }
}

private fun SykmeldingUnderArbeid.mapToOppgaveValues(): OppgaveValues? =
    OppgaveValues(
        fnrPasient = this.fnrPasient,
        // TODO implement custom scalars in GQL?
        behandletTidspunkt = this.sykmelding?.behandletTidspunkt?.toString(),
        // ISO 3166-1 alpha-2, 2-letter country codes
        skrevetLand = this.utenlandskSykmelding?.land,
        hoveddiagnose = this.sykmelding?.medisinskVurdering?.hovedDiagnose?.let {
            DiagnoseValue(
                kode = it.kode,
                tekst = it.tekst,
                system = it.system,
            )
        },
        biDiagnoser = this.sykmelding?.medisinskVurdering?.biDiagnoser?.map {
            DiagnoseValue(
                kode = it.kode,
                tekst = it.tekst,
                system = it.system,
            )
        },
    )

package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.pdl.PdlClient
import no.nav.sykdig.digitalisering.pdl.toFormattedNameString
import no.nav.sykdig.digitalisering.saf.SafClient
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.Adresse
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.DigitaliseringsoppgaveRespons
import no.nav.sykdig.generated.types.Person
import no.nav.sykdig.logger

@DgsComponent
class OppgaveDataFetcher(
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
    private val oppgaveRepository: OppgaveRepository,
    private val safClient: SafClient,
    private val pdlClient: PdlClient
) {
    private val log = logger()

    @DgsQuery(field = "oppgave")
    fun getOppgave(@InputArgument oppgaveId: String): DigitaliseringsoppgaveRespons {

        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        // hent navn og adresse fra PDL
        // Mer presis feilhåndtering
        // utvid format med SykmeldingUnderArbeid
        if (oppgave != null) {
            if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(oppgave.fnr)) {
                log.warn("Innlogget bruker har ikke tilgang til oppgave med id $oppgaveId")
                return DigitaliseringsoppgaveRespons(
                    digitaliseringsoppgave = null,
                    error = "Ikke tilgang til oppgave"
                )
            }
            try {
                val pdf = safClient.hentPdfFraSaf(
                    journalpostId = oppgave.journalpostId,
                    dokumentInfoId = oppgave.dokumentInfoId ?: "",
                    sykmeldingId = oppgave.sykmeldingId.toString()
                )
                val person = pdlClient.hentPerson(fnr = oppgave.fnr, sykmeldingId = oppgave.sykmeldingId.toString())
                return DigitaliseringsoppgaveRespons(
                    digitaliseringsoppgave = Digitaliseringsoppgave(
                        oppgaveId = oppgave.oppgaveId,
                        sykmeldingId = oppgave.sykmeldingId.toString(),
                        person = Person(
                            fnr = person.fnr,
                            navn = person.navn.toFormattedNameString(),
                            adresser = if (person.bostedsadresse?.vegadresse != null) {
                                listOf(Adresse(gateadresse = person.bostedsadresse.vegadresse.adressenavn, postnummer = person.bostedsadresse.vegadresse.postnummer, poststed = null, land = null, type = "BOSTED"))
                            } else {
                                emptyList()
                            }
                        ),
                        pdf = pdf.decodeToString()
                    ),
                    error = null
                )
            } catch (e: Exception) {
                log.error("Noe gikk galt ved henting av oppgave med id $oppgaveId")
                return DigitaliseringsoppgaveRespons(
                    digitaliseringsoppgave = null,
                    error = "Noe gikk galt ved henting av oppgave"
                )
            }
        } else {
            log.warn("Fant ikke oppgave med id $oppgaveId")
            return DigitaliseringsoppgaveRespons(
                digitaliseringsoppgave = null,
                error = "Fant ikke oppgave"
            )
        }
    }
}

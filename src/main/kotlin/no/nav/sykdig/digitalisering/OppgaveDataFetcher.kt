package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.saf.SafClient
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.DigitaliseringsoppgaveRespons
import no.nav.sykdig.generated.types.Person
import no.nav.sykdig.logger
import no.nav.sykdig.tilgangskontroll.SyfoTilgangskontrollOboClient

@DgsComponent
class OppgaveDataFetcher(
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient,
    private val oppgaveRepository: OppgaveRepository,
    private val safClient: SafClient
) {
    private val log = logger()

    @DgsQuery(field = "oppgave")
    fun getOppgave(@InputArgument oppgaveId: String): DigitaliseringsoppgaveRespons {

        val oppgave = oppgaveRepository.getOppgave(oppgaveId)
        // hent navn og adresse fra PDL
        // hent PDF fra SAF

       // utvid format med SykmeldingUnderArbeid
        // Mer presis feilhåndtering
        // utvid format med SykmeldingUnderArbeid
        return if (oppgave != null) {
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
                DigitaliseringsoppgaveRespons(
                    digitaliseringsoppgave = Digitaliseringsoppgave(
                        oppgaveId = oppgave.oppgaveId,
                        sykmeldingId = oppgave.sykmeldingId.toString(),
                        person = Person(
                            fnr = oppgave.fnr,
                            navn = null,
                            adresser = emptyList()
                        ),
                        pdf = pdf.decodeToString()
                    ),
                    error = null
                )
            } catch (e: Exception) {
                log.error("Noe gikk galt ved henting av PDF")
                return DigitaliseringsoppgaveRespons(
                    digitaliseringsoppgave = null,
                    error = "Noe gikk galt ved henting av PDF"
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

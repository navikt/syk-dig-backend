package no.nav.sykdig.nasjonal.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.papirsykmelding.mapToNasjonalOppgave
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.*
import no.nav.sykdig.nasjonal.services.NasjonalDbService
import no.nav.sykdig.nasjonal.mapping.mapToSmRegistreringManuell
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.IkkeTilgangException
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.tilgangskontroll.OppgaveSecurityService
import org.springframework.security.access.prepost.PostAuthorize
import java.util.UUID


@DgsComponent
class NasjonalOppgaveDataFetcher(
    private val nasjonalDbService: NasjonalDbService,
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val oppgaveSecurityService: OppgaveSecurityService,
) {

    companion object {
        val log = applog()
        val securelog = securelog()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/dgs/nasjonal/oppgave/{oppgaveId}')")
    @DgsQuery(field = DgsConstants.QUERY.NasjonalOppgave)
    fun getNasjonalOppgave(@InputArgument oppgaveId: String, dfe: DataFetchingEnvironment): NasjonalOppgaveResult? {
        log.info("Henter najsonal oppgave med id $oppgaveId")
        val oppgave = nasjonalDbService.getOppgaveByOppgaveId(oppgaveId)
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
        val oppgave = nasjonalDbService.getOppgaveBySykmeldingId(sykmeldingId)
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

    @DgsMutation(field = DgsConstants.MUTATION.LagreNasjonalOppgave)
    suspend fun lagreNasjonalOppgave(
        @InputArgument oppgaveId: String,
        @InputArgument navEnhet: String,
        @InputArgument sykmeldingValues: NasjonalSykmeldingValues,
        @InputArgument status: SykmeldingUnderArbeidStatus,
        dfe: DataFetchingEnvironment): LagreOppgaveResult {
        val callId = UUID.randomUUID().toString()

        when (status) {
            SykmeldingUnderArbeidStatus.UNDER_ARBEID -> {
                if (oppgaveSecurityService.hasAccessToNasjonalOppgave(oppgaveId, "/dgs/nasjonal/oppgave/{oppgaveId}/send")) {
                    val ferdigstiltSykmeldingValues = mapToSmRegistreringManuell(sykmeldingValues)
                    return nasjonalOppgaveService.sendOppgave(
                        ferdigstiltSykmeldingValues,
                        navEnhet,
                        callId,
                        oppgaveId
                    ).also { log.info("Registrert nasjonal sykmelding med oppgaveId $oppgaveId") }
                } else {
                    log.warn("Veileder har ikke tilgang til å registrere oppgave med oppgaveId $oppgaveId")
                    throw IkkeTilgangException("Veileder har ikke tilgang til å registrere oppgave med oppgaveId $oppgaveId, status: $status")
                }
            }

            SykmeldingUnderArbeidStatus.FERDIGSTILT -> {
                if (oppgaveSecurityService.hasSuperUserAccessToNasjonalSykmelding(null, oppgaveId, "/dgs/nasjonal/sykmelding/korriger")) {
                    val oppdatertSykmeldingValues = mapToSmRegistreringManuell(sykmeldingValues)
                    return nasjonalOppgaveService.korrigerSykmeldingMedOppgaveId(
                        oppgaveId,
                        navEnhet,
                        callId,
                        oppdatertSykmeldingValues
                    ).also { log.info("Korrigert nasjonal sykmelding med oppgaveId $oppgaveId") }
                } else {
                    log.warn("Veileder har ikke tilgang til å korriger sykmelding med oppgaveId $oppgaveId")
                    throw IkkeTilgangException("Veileder har ikke tilgang til å korriger sykmelding med oppgaveId $oppgaveId")
                }
            }
        }
    }
}

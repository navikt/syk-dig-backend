package no.nav.sykdig.nasjonal.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.papirsykmelding.mapToNasjonalOppgave
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.NasjonalOppgaveResult
import no.nav.sykdig.generated.types.NasjonalOppgaveStatus
import no.nav.sykdig.generated.types.NasjonalOppgaveStatusEnum
import no.nav.sykdig.generated.types.NasjonalSykmeldingResult
import no.nav.sykdig.generated.types.NasjonalSykmeldingStatus
import no.nav.sykdig.generated.types.NasjonalOppdatertSykmeldingStatusEnum
import no.nav.sykdig.generated.types.NasjonalSykmeldingValues
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidStatus
import no.nav.sykdig.generated.types.LagreSykmeldingResult
import no.nav.sykdig.nasjonal.mapping.mapToSmRegistreringManuell
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.nasjonal.services.NasjonalSykmeldingService
import no.nav.sykdig.shared.applog
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import java.util.UUID


@DgsComponent
class NasjonalOppgaveDataFetcher(
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val nasjonalSykmeldingService: NasjonalSykmeldingService
) {

    companion object {
        val log = applog()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/dgs/nasjonal/oppgave/{oppgaveId}')")
    @DgsQuery(field = DgsConstants.QUERY.NasjonalOppgave)
    fun getNasjonalOppgave(@InputArgument oppgaveId: String, dfe: DataFetchingEnvironment): NasjonalOppgaveResult? {
        log.info("Henter najsonal oppgave med id $oppgaveId")
        val oppgave = nasjonalOppgaveService.getOppgave(oppgaveId)
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
        val oppgave = nasjonalOppgaveService.findBySykmeldingId(sykmeldingId)
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

    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/dgs/nasjonal/oppgave/{oppgaveId}/lagre')")
    @DgsMutation(field = DgsConstants.MUTATION.LagreNasjonalOppgave)
    suspend fun lagreNasjonalOppgave(
        @InputArgument oppgaveId: String,
        @InputArgument navEnhet: String,
        @InputArgument sykmeldingValues: NasjonalSykmeldingValues,
        @InputArgument status: SykmeldingUnderArbeidStatus,
        dfe: DataFetchingEnvironment): LagreSykmeldingResult? {
        val callId = UUID.randomUUID().toString()

        when (status) {
            SykmeldingUnderArbeidStatus.UNDER_ARBEID -> {
                val uferdigSykmeldingValues = mapToSmRegistreringManuell(sykmeldingValues)
                nasjonalSykmeldingService.sendPapirsykmeldingOppgaveGraphql(
                    uferdigSykmeldingValues,
                    navEnhet,
                    callId,
                    oppgaveId
                )
                log.info("Registrert nasjonal sykmelding med oppgaveId $oppgaveId")

                return NasjonalOppgaveStatus(
                    oppgaveId,
                    status = NasjonalOppgaveStatusEnum.FERDIGSTILT,
                )
            }

            SykmeldingUnderArbeidStatus.FERDIGSTILT -> {
                val ferdigstiltSykmeldingValues = mapToSmRegistreringManuell(sykmeldingValues)
                nasjonalSykmeldingService.korrigerSykmeldingMedOppgaveId(
                    oppgaveId,
                    navEnhet,
                    callId,
                    ferdigstiltSykmeldingValues
                )
                log.info("Korrigert nasjonal sykmelding med oppgaveId $oppgaveId")

                return NasjonalOppgaveStatus(
                    oppgaveId, //sykmeldingId
                    status = NasjonalOppgaveStatusEnum.FERDIGSTILT,
                )
            }
        }
    }
}
package no.nav.sykdig.nasjonal.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import graphql.schema.DataFetchingEnvironment
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.sykdig.digitalisering.papirsykmelding.mapToNasjonalOppgave
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.*
import no.nav.sykdig.nasjonal.helsenett.SykmelderService
import no.nav.sykdig.nasjonal.mapping.mapSykmelder
import no.nav.sykdig.nasjonal.services.NasjonalDbService
import no.nav.sykdig.nasjonal.mapping.mapToSmRegistreringManuell
import no.nav.sykdig.nasjonal.services.NasjonalOppgaveService
import no.nav.sykdig.nasjonal.services.PasientNavnService
import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.exceptions.IkkeTilgangException
import no.nav.sykdig.shared.exceptions.SykmelderNotFoundException
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.tilgangskontroll.OppgaveSecurityService
import org.springframework.security.access.prepost.PostAuthorize
import org.springframework.security.access.prepost.PreAuthorize
import java.util.UUID

@DgsComponent
class NasjonalOppgaveDataFetcher(
    private val nasjonalDbService: NasjonalDbService,
    private val nasjonalOppgaveService: NasjonalOppgaveService,
    private val oppgaveSecurityService: OppgaveSecurityService,
    private val pasientNavnService: PasientNavnService,
    private val sykmelderService: SykmelderService,
) {

    companion object {
        val log = applog()
        val securelog = securelog()
    }

    @PostAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/dgs/nasjonal/oppgave/{oppgaveId}')")
    @DgsQuery(field = DgsConstants.QUERY.NasjonalOppgave)
    @WithSpan
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
    @WithSpan
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
    @WithSpan
    suspend fun lagreNasjonalOppgave(
        @InputArgument oppgaveId: String,
        @InputArgument navEnhet: String,
        @InputArgument sykmeldingValues: NasjonalSykmeldingValues,
        @InputArgument status: SykmeldingUnderArbeidStatus,
        dfe: DataFetchingEnvironment,
    ): LagreOppgaveResult {
        val callId = UUID.randomUUID().toString()

        when (status) {
            SykmeldingUnderArbeidStatus.UNDER_ARBEID -> {
                if (oppgaveSecurityService.hasAccessToNasjonalOppgave(oppgaveId, "/dgs/nasjonal/oppgave/{oppgaveId}/send")) {
                    val ferdigstiltSykmeldingValues = mapToSmRegistreringManuell(sykmeldingValues)
                    return nasjonalOppgaveService.sendOppgave(
                        ferdigstiltSykmeldingValues,
                        navEnhet,
                        callId,
                        oppgaveId,
                    ).also { log.info("Registrert nasjonal sykmelding med oppgaveId $oppgaveId") }
                } else {
                    log.warn("Veileder har ikke tilgang til å registrere oppgave med oppgaveId $oppgaveId")
                    throw IkkeTilgangException("Veileder har ikke tilgang til å registrere oppgave med oppgaveId $oppgaveId, status: $status")
                }
            }

            SykmeldingUnderArbeidStatus.FERDIGSTILT -> {
                if (oppgaveSecurityService.hasSuperUserAccessToNasjonalSykmelding(oppgaveId, "/dgs/nasjonal/sykmelding/korriger")) {
                    val oppdatertSykmeldingValues = mapToSmRegistreringManuell(sykmeldingValues)
                    return nasjonalOppgaveService.korrigerSykmeldingMedOppgaveId(
                        oppgaveId,
                        navEnhet,
                        callId,
                        oppdatertSykmeldingValues,
                    ).also { log.info("Korrigert nasjonal sykmelding med oppgaveId $oppgaveId") }
                } else {
                    log.warn("Veileder har ikke tilgang til å korriger sykmelding med oppgaveId $oppgaveId")
                    throw IkkeTilgangException("Veileder har ikke tilgang til å korriger sykmelding med oppgaveId $oppgaveId")
                }
            }
        }
    }

    @DgsQuery(field = DgsConstants.QUERY.PasientNavn)
    fun getPasientNavn(dfe: DataFetchingEnvironment): Navn {
        val callId = UUID.randomUUID().toString()
        log.info("Henter person med callId $callId")

        val fnr: String = dfe.graphQlContext.get("pasient_fnr")
        val personNavn =
            pasientNavnService.getPersonNavn(
                id = fnr,
                callId = callId,
            )
        return personNavn
    }

    @DgsQuery(field = DgsConstants.QUERY.Sykmelder)
    fun getSykmelder(@InputArgument hprNummer: String, dfe: DataFetchingEnvironment): Sykmelder? {
        if (hprNummer.isBlank() || !hprNummer.all { it.isDigit() }) {
            log.info("Ugyldig path parameter: hprNummer")
            securelog.info("Ugyldig path parameter: hprNummer: $hprNummer")
            throw DgsInvalidInputArgumentException("Ugyldig path parameter: hprNummer")
        }
        val callId = UUID.randomUUID().toString()
        securelog.info("Henter sykmelder med callId $callId and hprNummer = $hprNummer")
        try {
            val sykmelder = sykmelderService.getSykmelder(hprNummer, callId)
            return mapSykmelder(sykmelder)
        } catch (_: SykmelderNotFoundException) {
            return null
        } catch (e: Exception) {
            throw e
        }

    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/dgs/oppgave/{oppgaveId}/tilgosys')")
    @DgsMutation(field = DgsConstants.MUTATION.OppgaveTilbakeTilGosysNasjonal)
    @WithSpan
    fun sendOppgaveTilGosys(@InputArgument oppgaveId: String, dfe: DataFetchingEnvironment): LagreNasjonalOppgaveStatus {
        if (oppgaveId.isBlank()) {
            log.info("Mangler oppgaveId for å kunne sende nasjonal oppgave til Gosys")
            throw DgsInvalidInputArgumentException("Mangler oppgaveId for å kunne sende nasjonal oppgave til Gosys")
        }
        log.info("Sender nasjonal oppgave med id $oppgaveId til Gosys")
        nasjonalOppgaveService.oppgaveTilGosys(oppgaveId)
        return LagreNasjonalOppgaveStatus(
            oppgaveId = oppgaveId,
            status = LagreNasjonalOppgaveStatusEnum.IKKE_EN_SYKMELDING,
        )
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToNasjonalOppgave(#oppgaveId, '/dgs/oppgave/{oppgaveId}/avvis')")
    @DgsMutation(field = DgsConstants.MUTATION.AvvisNasjonalOppgave)
    @WithSpan
    suspend fun avvisOppgave(@InputArgument oppgaveId: String, @InputArgument avvisningsgrunn: String?, @InputArgument navEnhet: String, dfe: DataFetchingEnvironment): LagreNasjonalOppgaveStatus {
        log.info("Forsøker å avvise nasjonal oppgave med oppgaveId: $oppgaveId")
        return nasjonalOppgaveService.avvisOppgave(oppgaveId, avvisningsgrunn, navEnhet)
    }
}

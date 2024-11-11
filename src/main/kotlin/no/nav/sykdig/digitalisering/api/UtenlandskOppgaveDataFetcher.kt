package no.nav.sykdig.digitalisering.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.exceptions.DgsEntityNotFoundException
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.applog
import no.nav.sykdig.digitalisering.UtenlandskOppgaveService
import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.digitalisering.mapToDigitaliseringsoppgave
import no.nav.sykdig.digitalisering.mapToDigitalisertSykmelding
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.UferdigRegisterOppgaveValues
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.DigitaliseringsoppgaveResult
import no.nav.sykdig.generated.types.DigitaliseringsoppgaveStatus
import no.nav.sykdig.generated.types.DigitaliseringsoppgaveStatusEnum
import no.nav.sykdig.generated.types.DigitalisertSykmeldingResult
import no.nav.sykdig.generated.types.OppdatertSykmeldingStatus
import no.nav.sykdig.generated.types.OppdatertSykmeldingStatusEnum
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidStatus
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.utils.toOffsetDateTimeAtNoon
import no.nav.sykdig.utils.validateDiagnose
import org.springframework.security.access.prepost.PreAuthorize
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@DgsComponent
class UtenlandskOppgaveDataFetcher(
    val utenlandskOppgaveService: UtenlandskOppgaveService,
) {
    companion object {
        private val log = applog()
    }


    @PreAuthorize("@oppgaveSecurityService.hasAccessToSykmelding(#sykmeldingId)")
    @DgsQuery(field = DgsConstants.QUERY.DigitalisertSykmelding)
    fun getDigitalisertSykmelding(
        @InputArgument sykmeldingId: String,
        dfe: DataFetchingEnvironment,
    ): DigitalisertSykmeldingResult? {
        val oppgave = utenlandskOppgaveService.getDigitalisertSykmelding(sykmeldingId)
        val state = utenlandskOppgaveService.checkOppgaveState(oppgave.oppgaveDbModel)
        if(state != OppdatertSykmeldingStatusEnum.FERDIGSTILT) {
            return OppdatertSykmeldingStatus(
                sykmeldingId,
                state
            )
        }
        return mapToDigitalisertSykmelding(oppgave)
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @DgsQuery(field = DgsConstants.QUERY.Oppgave)
    fun getOppgave(
        @InputArgument oppgaveId: String,
        dfe: DataFetchingEnvironment,
    ): DigitaliseringsoppgaveResult {
        try {
            val oppgave = utenlandskOppgaveService.getDigitaiseringsoppgave(oppgaveId)

            if (oppgave.oppgaveDbModel.tilbakeTilGosys) {
                log.info("Oppgave med $oppgaveId er markert som tilbake til Gosys, returnerer status IKKE_EN_SYKMELDING")

                return DigitaliseringsoppgaveStatus(
                    oppgaveId = oppgaveId,
                    status = DigitaliseringsoppgaveStatusEnum.IKKE_EN_SYKMELDING,
                )
            } else if (oppgave.oppgaveDbModel.ferdigstilt != null) {
                log.info("Oppgave med $oppgaveId er markert som ferdigstilt, returnerer status FERDIGSTILT")

                return DigitaliseringsoppgaveStatus(
                    oppgaveId = oppgaveId,
                    status = DigitaliseringsoppgaveStatusEnum.FERDIGSTILT,
                )
            }

            log.info("Oppgave med $oppgaveId er ikke ferdigstilt, returnerer oppgave")
            return mapToDigitaliseringsoppgave(oppgave)
        } catch (error: DgsEntityNotFoundException) {
            log.info("Oppgave med $oppgaveId kastet DgsEntityNotFoundException, returnerer status FINNES_IKKE")
            return DigitaliseringsoppgaveStatus(
                oppgaveId = oppgaveId,
                status = DigitaliseringsoppgaveStatusEnum.FINNES_IKKE,
            )
        } catch (error: RuntimeException) {
            log.error("Oppgave med $oppgaveId kastet et ukjent feil! \uD83D\uDE2E", error)

            return DigitaliseringsoppgaveStatus(
                oppgaveId = oppgaveId,
                status = DigitaliseringsoppgaveStatusEnum.FINNES_IKKE,
            )
        }
    }
    @PreAuthorize("@oppgaveSecurityService.hasAccessToSykmelding(#sykmeldingId)")
    @DgsMutation(field = DgsConstants.MUTATION.OppdaterDigitalisertSykmelding)
    fun oppdaterSykmelding(
        @InputArgument sykmeldingId: String,
        @InputArgument enhetId: String,
        @InputArgument values: SykmeldingUnderArbeidValues,
        dfe: DataFetchingEnvironment,
    ): OppdatertSykmeldingStatus {
        val navEmail: String = dfe.graphQlContext.get("username")
        val ferdistilltRegisterOppgaveValues = validateRegisterOppgaveValues(values)
        utenlandskOppgaveService.oppdaterDigitalisertSykmelding(
            sykmeldingId = sykmeldingId,
            enhetId = enhetId,
            values = ferdistilltRegisterOppgaveValues,
            navEmail = navEmail,
        )
        return OppdatertSykmeldingStatus(
            sykmeldingId = sykmeldingId,
            status = OppdatertSykmeldingStatusEnum.OPPDATERT
        )
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @DgsMutation(field = DgsConstants.MUTATION.Lagre)
    fun lagreOppgave(
        @InputArgument oppgaveId: String,
        @InputArgument enhetId: String,
        @InputArgument values: SykmeldingUnderArbeidValues,
        @InputArgument status: SykmeldingUnderArbeidStatus,
        dfe: DataFetchingEnvironment,
    ): DigitaliseringsoppgaveResult {
        val navEmail: String = dfe.graphQlContext.get("username")
        when (status) {
            SykmeldingUnderArbeidStatus.FERDIGSTILT -> {
                val ferdistilltRegisterOppgaveValues = validateRegisterOppgaveValues(values)
                utenlandskOppgaveService.ferdigstillOppgave(
                    oppgaveId = oppgaveId,
                    navEpost = navEmail,
                    values = ferdistilltRegisterOppgaveValues,
                    enhetId = enhetId,
                ).also { log.info("Ferdigstilt oppgave med id $oppgaveId") }

                return DigitaliseringsoppgaveStatus(
                    oppgaveId = oppgaveId,
                    status = DigitaliseringsoppgaveStatusEnum.FERDIGSTILT,
                )
            }

            SykmeldingUnderArbeidStatus.UNDER_ARBEID -> {
                val uferdigRegisterOppgaveValues = uferdigRegisterOppgaveValues(values)
                utenlandskOppgaveService.updateOppgave(
                    oppgaveId = oppgaveId,
                    values = uferdigRegisterOppgaveValues,
                    navEpost = navEmail,
                ).also { log.info("Lagret oppgave med id $oppgaveId") }

                return mapToDigitaliseringsoppgave(utenlandskOppgaveService.getDigitaiseringsoppgave(oppgaveId))
            }
        }
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @DgsMutation(field = DgsConstants.MUTATION.OppgaveTilbakeTilGosys)
    fun oppgaveTilbakeTilGosys(
        @InputArgument oppgaveId: String,
        dfe: DataFetchingEnvironment,
    ): DigitaliseringsoppgaveStatus {
        val navEpost: String = dfe.graphQlContext.get("username")
        val navIdent: String = dfe.graphQlContext.get("nav_ident")
        utenlandskOppgaveService.ferdigstillOppgaveSendTilGosys(
            oppgaveId = oppgaveId,
            navIdent = navIdent,
            navEpost = navEpost,
        )

        return DigitaliseringsoppgaveStatus(
            oppgaveId = oppgaveId,
            status = DigitaliseringsoppgaveStatusEnum.IKKE_EN_SYKMELDING,
        )
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @DgsMutation(field = DgsConstants.MUTATION.Avvis)
    fun avvis(
        @InputArgument oppgaveId: String,
        @InputArgument avvisningsgrunn: Avvisingsgrunn,
        @InputArgument enhetId: String,
        @InputArgument avvisningsgrunnAnnet: String?,
        dfe: DataFetchingEnvironment,
    ): DigitaliseringsoppgaveStatus {
        val navEpost: String = dfe.graphQlContext.get("username")
        val navIdent: String = dfe.graphQlContext.get("nav_ident")

        utenlandskOppgaveService.avvisOppgave(
            oppgaveId = oppgaveId,
            navIdent = navIdent,
            navEpost = navEpost,
            enhetId = enhetId,
            avvisningsgrunn = avvisningsgrunn,
            avvisningsgrunnAnnet = avvisningsgrunnAnnet,
        )

        return DigitaliseringsoppgaveStatus(
            oppgaveId = oppgaveId,
            status = DigitaliseringsoppgaveStatusEnum.AVVIST,
        )
    }
}

private fun validateRegisterOppgaveValues(values: SykmeldingUnderArbeidValues): FerdistilltRegisterOppgaveValues {
    val behandletTidspunkt = values.behandletTidspunkt.toOffsetDateTimeAtNoon()
    requireNotEmptyOrNull(values.fnrPasient) { "Fødselsnummer til pasient må være satt" }
    requireNotEmptyOrNull(behandletTidspunkt) { "Tidspunkt for behandling må være satt" }
    requireNotEmptyOrNull(values.skrevetLand) { "Landet sykmeldingen er skrevet må være satt" }
    requireNotEmptyOrNull(values.perioder) { "Sykmeldingsperioder må være satt" }
    requireNotEmptyOrNull(values.hovedDiagnose) { "Hoveddiagnose må være satt" }
    requireNotEmptyOrNull(values.biDiagnoser) { "Bidiagnoser må være satt" }

    validateHovedDiagnose(values.hovedDiagnose)
    validateBiDiagnoser(values.biDiagnoser)

    return FerdistilltRegisterOppgaveValues(
        fnrPasient = values.fnrPasient,
        behandletTidspunkt = behandletTidspunkt,
        skrevetLand = values.skrevetLand,
        perioder = values.perioder,
        hovedDiagnose = values.hovedDiagnose,
        biDiagnoser = values.biDiagnoser,
        folkeRegistertAdresseErBrakkeEllerTilsvarende = values.folkeRegistertAdresseErBrakkeEllerTilsvarende,
        erAdresseUtland = values.erAdresseUtland,
    )
}

private fun validateHovedDiagnose(hovedDiagnose: DiagnoseInput?) {
    if (hovedDiagnose != null) {
        validateDiagnose(hovedDiagnose)
    }
}

private fun validateBiDiagnoser(biDiagnoser: List<DiagnoseInput>?) {
    if (!biDiagnoser.isNullOrEmpty()) {
        biDiagnoser.forEach { biDiagnose ->
            validateDiagnose(biDiagnose)
        }
    }
}

private fun uferdigRegisterOppgaveValues(sykmeldingUnderArbeidValues: SykmeldingUnderArbeidValues): RegisterOppgaveValues {
    return UferdigRegisterOppgaveValues(
        fnrPasient = sykmeldingUnderArbeidValues.fnrPasient,
        behandletTidspunkt = sykmeldingUnderArbeidValues.behandletTidspunkt.toOffsetDateTimeAtNoon(),
        skrevetLand = sykmeldingUnderArbeidValues.skrevetLand,
        perioder = sykmeldingUnderArbeidValues.perioder,
        hovedDiagnose = sykmeldingUnderArbeidValues.hovedDiagnose,
        biDiagnoser = sykmeldingUnderArbeidValues.biDiagnoser,
        folkeRegistertAdresseErBrakkeEllerTilsvarende = sykmeldingUnderArbeidValues.folkeRegistertAdresseErBrakkeEllerTilsvarende,
        erAdresseUtland = sykmeldingUnderArbeidValues.erAdresseUtland,
    )
}

@OptIn(ExperimentalContracts::class)
inline fun <T : Any> requireNotEmptyOrNull(
    value: T?,
    lazyMessage: () -> Any,
): T {
    contract {
        returns() implies (value != null)
    }

    when {
        value is String && value.isEmpty() -> {
            throw ClientException(lazyMessage().toString())
        }

        value == null -> {
            val message = lazyMessage()
            throw ClientException(message.toString())
        }

        else -> {
            return value
        }
    }
}

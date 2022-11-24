package no.nav.sykdig.digitalisering.api

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.DigitaliseringsoppgaveService
import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.digitalisering.mapToDigitaliseringsoppgave
import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.RegisterOppgaveValues
import no.nav.sykdig.digitalisering.model.UferdigRegisterOppgaveValues
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidStatus
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.logger
import no.nav.sykdig.utils.toOffsetDateTimeAtNoon
import no.nav.sykdig.utils.validateDiagnose
import org.springframework.security.access.prepost.PreAuthorize
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@DgsComponent
class DigitaliseringsoppgaveDataFetcher(
    val digitaliseringsoppgaveService: DigitaliseringsoppgaveService
) {
    companion object {
        private val log = logger()
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @DgsQuery(field = DgsConstants.QUERY.Oppgave)
    fun getOppgave(@InputArgument oppgaveId: String): Digitaliseringsoppgave {
        return mapToDigitaliseringsoppgave(digitaliseringsoppgaveService.getDigitaiseringsoppgave(oppgaveId))
    }

    @PreAuthorize("@oppgaveSecurityService.hasAccessToOppgave(#oppgaveId)")
    @DgsMutation(field = DgsConstants.MUTATION.Lagre)
    fun lagreOppgave(
        @InputArgument oppgaveId: String,
        @InputArgument enhetId: String,
        @InputArgument values: SykmeldingUnderArbeidValues,
        @InputArgument status: SykmeldingUnderArbeidStatus,
        dfe: DataFetchingEnvironment,
    ): Digitaliseringsoppgave {

        val ident: String = dfe.graphQlContext.get("username")

        when (status) {
            SykmeldingUnderArbeidStatus.FERDIGSTILT -> {
                val ferdistilltRegisterOppgaveValues = validateRegisterOppgaveValues(values)
                digitaliseringsoppgaveService.ferdigstillOppgave(
                    oppgaveId = oppgaveId,
                    ident = ident,
                    values = ferdistilltRegisterOppgaveValues,
                    enhetId = enhetId
                ).also { log.info("Ferdigstilt oppgave med id $oppgaveId") }
            }
            SykmeldingUnderArbeidStatus.UNDER_ARBEID -> {
                val uferdigRegisterOppgaveValues = uferdigRegisterOppgaveValus(values)
                digitaliseringsoppgaveService.updateOppgave(
                    oppgaveId = oppgaveId,
                    values = uferdigRegisterOppgaveValues,
                    ident = ident
                ).also { log.info("Lagret oppgave med id $oppgaveId") }
            }
        }

        return mapToDigitaliseringsoppgave(digitaliseringsoppgaveService.getDigitaiseringsoppgave(oppgaveId))
    }
}
private fun validateRegisterOppgaveValues(
    values: SykmeldingUnderArbeidValues,
): FerdistilltRegisterOppgaveValues {
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
        harAndreRelevanteOpplysninger = values.harAndreRelevanteOpplysninger
    )
}

private fun validateHovedDiagnose(houvedDiagnose: DiagnoseInput?) {

    if (houvedDiagnose != null) {
        validateDiagnose(houvedDiagnose)
    }
}

private fun validateBiDiagnoser(biDiagnoser: List<DiagnoseInput>?) {

    if (!biDiagnoser.isNullOrEmpty()) {
        biDiagnoser.forEach { biDiagnose ->
            validateDiagnose(biDiagnose)
        }
    }
}

private fun uferdigRegisterOppgaveValus(sykmeldingUnderArbeidValues: SykmeldingUnderArbeidValues): RegisterOppgaveValues {
    return UferdigRegisterOppgaveValues(
        fnrPasient = sykmeldingUnderArbeidValues.fnrPasient,
        behandletTidspunkt = sykmeldingUnderArbeidValues.behandletTidspunkt.toOffsetDateTimeAtNoon(),
        skrevetLand = sykmeldingUnderArbeidValues.skrevetLand,
        perioder = sykmeldingUnderArbeidValues.perioder,
        hovedDiagnose = sykmeldingUnderArbeidValues.hovedDiagnose,
        biDiagnoser = sykmeldingUnderArbeidValues.biDiagnoser,
        harAndreRelevanteOpplysninger = sykmeldingUnderArbeidValues.harAndreRelevanteOpplysninger
    )
}

@OptIn(ExperimentalContracts::class)
inline fun <T : Any> requireNotEmptyOrNull(value: T?, lazyMessage: () -> Any): T {
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

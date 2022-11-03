package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.exceptions.ClientException
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidStatus
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.logger
import no.nav.sykdig.utils.toOffsetDateTimeAtNoon
import java.time.OffsetDateTime
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@DgsComponent
class OppgaveDataFetcher(
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
) {
    val log = logger()

    @DgsQuery(field = DgsConstants.QUERY.Oppgave)
    fun getOppgave(@InputArgument oppgaveId: String): Digitaliseringsoppgave {
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        val person = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString()
        )

        return mapToDigitaliseringsoppgave(oppgave, person)
    }

    @DgsMutation(field = DgsConstants.MUTATION.Lagre)
    fun lagreOppgave(
        @InputArgument oppgaveId: String,
        @InputArgument enhetId: String,
        @InputArgument values: SykmeldingUnderArbeidValues,
        @InputArgument status: SykmeldingUnderArbeidStatus,
        dfe: DataFetchingEnvironment,
    ): Digitaliseringsoppgave {
        val ident: String = dfe.graphQlContext.get("username")
        val oppgave = oppgaveService.getOppgave(oppgaveId)
        val person = personService.hentPerson(
            fnr = oppgave.fnr,
            sykmeldingId = oppgave.sykmeldingId.toString(),
        )

        if (status == SykmeldingUnderArbeidStatus.FERDIGSTILT) {
            val validatedValues = validateRegisterOppgaveValues(values)
            oppgaveService.ferigstillOppgave(
                oppgaveId = oppgaveId,
                ident = ident,
                values = values,
                validatedValues = validatedValues,
                enhetId = enhetId,
                person = person,
                oppgave = oppgave
            )
            log.info("Ferdigstilt oppgave med id $oppgaveId")
        } else {
            oppgaveService.updateOppgave(oppgaveId, values, ident)
            log.info("Lagret oppgave med id $oppgaveId")
        }

        return mapToDigitaliseringsoppgave(
            oppgave = oppgaveService.getOppgave(oppgaveId),
            person = person
        )
    }
}

data class ValidatedOppgaveValues(
    val fnrPasient: String,
    val behandletTidspunkt: OffsetDateTime,
    val skrevetLand: String,
    val perioder: List<PeriodeInput>,
    val hovedDiagnose: DiagnoseInput,
    val biDiagnoser: List<DiagnoseInput>,
)

private fun validateRegisterOppgaveValues(
    values: SykmeldingUnderArbeidValues,
): ValidatedOppgaveValues {
    val behandletTidspunkt = values.behandletTidspunkt.toOffsetDateTimeAtNoon()
    requireNotEmptyOrNull(values.fnrPasient) { "Fødselsnummer til pasient må være satt" }
    requireNotEmptyOrNull(behandletTidspunkt) { "Tidspunkt for behandling må være satt" }
    requireNotEmptyOrNull(values.skrevetLand) { "Landet sykmeldingen er skrevet må være satt" }
    requireNotEmptyOrNull(values.perioder) { "Sykmeldingsperioder må være satt" }
    requireNotEmptyOrNull(values.hovedDiagnose) { "Hoveddiagnose må være satt" }
    requireNotEmptyOrNull(values.biDiagnoser) { "Bidiagnoser må være satt" }

    return ValidatedOppgaveValues(
        fnrPasient = values.fnrPasient,
        behandletTidspunkt = behandletTidspunkt,
        skrevetLand = values.skrevetLand,
        perioder = values.perioder,
        hovedDiagnose = values.hovedDiagnose,
        biDiagnoser = values.biDiagnoser,
    )
}

@OptIn(ExperimentalContracts::class)
public inline fun <T : Any> requireNotEmptyOrNull(value: T?, lazyMessage: () -> Any): T {
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

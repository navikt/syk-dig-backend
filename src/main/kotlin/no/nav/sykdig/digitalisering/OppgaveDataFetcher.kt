package no.nav.sykdig.digitalisering

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import graphql.schema.DataFetchingEnvironment
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.generated.DgsConstants
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.Digitaliseringsoppgave
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidStatus
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import no.nav.sykdig.utils.toOffsetDateTimeAtNoon
import java.time.OffsetDateTime

@DgsComponent
class OppgaveDataFetcher(
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
) {
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
        } else {
            oppgaveService.updateOppgave(oppgaveId, values, ident)
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
    requireNotNull(values.fnrPasient) { "fnr pasient må være satt" }
    requireNotNull(behandletTidspunkt) { "behandlettidspunkt må være satt" }
    requireNotNull(values.skrevetLand) { "skrevetland må være satt" }
    requireNotNull(values.perioder) { "perioder må være satt" }
    requireNotNull(values.hovedDiagnose) { "hoveddiagnose må være satt" }
    requireNotNull(values.biDiagnoser) { "bidiagnoser må være satt" }

    return ValidatedOppgaveValues(
        fnrPasient = values.fnrPasient,
        behandletTidspunkt = behandletTidspunkt,
        skrevetLand = values.skrevetLand,
        perioder = values.perioder,
        hovedDiagnose = values.hovedDiagnose,
        biDiagnoser = values.biDiagnoser,
    )
}

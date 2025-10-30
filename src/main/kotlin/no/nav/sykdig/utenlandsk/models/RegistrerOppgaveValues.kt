package no.nav.sykdig.utenlandsk.models

import java.time.OffsetDateTime
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput

interface RegisterOppgaveValues {
    val fnrPasient: String
    val behandletTidspunkt: OffsetDateTime?
    val skrevetLand: String?
    val perioder: List<PeriodeInput>?
    val hovedDiagnose: DiagnoseInput?
    val biDiagnoser: List<DiagnoseInput>?
    val folkeRegistertAdresseErBrakkeEllerTilsvarende: Boolean?
    val erAdresseUtland: Boolean?
}

data class FerdistilltRegisterOppgaveValues(
    override val fnrPasient: String,
    override val behandletTidspunkt: OffsetDateTime,
    override val skrevetLand: String,
    override val perioder: List<PeriodeInput>,
    override val hovedDiagnose: DiagnoseInput,
    override val biDiagnoser: List<DiagnoseInput>,
    override val folkeRegistertAdresseErBrakkeEllerTilsvarende: Boolean?,
    override val erAdresseUtland: Boolean?,
) : RegisterOppgaveValues

data class UferdigRegisterOppgaveValues(
    override val fnrPasient: String,
    override val behandletTidspunkt: OffsetDateTime?,
    override val skrevetLand: String?,
    override val perioder: List<PeriodeInput>?,
    override val hovedDiagnose: DiagnoseInput?,
    override val biDiagnoser: List<DiagnoseInput>?,
    override val folkeRegistertAdresseErBrakkeEllerTilsvarende: Boolean?,
    override val erAdresseUtland: Boolean?,
) : RegisterOppgaveValues

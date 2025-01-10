package no.nav.sykdig.nasjonal.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.shared.*
import no.nav.sykdig.shared.exceptions.ValidationException
import no.nav.sykdig.nasjonal.clients.RegelClient
import no.nav.sykdig.nasjonal.models.RuleHitCustomError
import no.nav.sykdig.nasjonal.models.WhitelistedRuleHit
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.utenlandsk.models.ReceivedSykmelding
import org.springframework.stereotype.Service
import java.time.LocalDate

const val HPR_GODKJENNING_KODE = 7704
@Service
class NasjonalRegelvalideringService(private val regelClient: RegelClient) {
    val log = applog()
    suspend fun validerNasjonalSykmelding(receivedSykmelding: ReceivedSykmelding, smRegistreringManuell: SmRegistreringManuell, sykmeldingId: String, loggingMeta: LoggingMeta, oppgaveId: Int?, sykmelder: Sykmelder): ValidationResult {
        val validationResult = regelClient.valider(receivedSykmelding, sykmeldingId)
        log.info(
            "Resultat: {}, {}, {}",
            StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
            StructuredArguments.keyValue(
                "ruleHits",
                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName },
            ),
            StructuredArguments.fields(loggingMeta),
        )
        checkValidState(oppgaveId, smRegistreringManuell, sykmelder, validationResult)
        return validationResult
    }

    fun checkValidState(
        oppgaveId: Int?,
        smRegistreringManuell: SmRegistreringManuell,
        sykmelder: Sykmelder,
        validationResult: ValidationResult,
    ) {
        when {
            smRegistreringManuell.perioder.isEmpty() -> {
                val vr =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName = "periodeValidation",
                                    messageForSender =
                                        "Sykmeldingen må ha minst én periode oppgitt for å være gyldig",
                                    messageForUser =
                                        "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                throw ValidationException("ValidationException thrown for oppgaveId $oppgaveId and validationresult $vr")
            }
            harOverlappendePerioder(smRegistreringManuell.perioder) -> {
                val vr =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName = "overlappendePeriodeValidation",
                                    messageForSender = "Sykmeldingen har overlappende perioder",
                                    messageForUser =
                                        "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                throw ValidationException("ValidationException thrown for oppgaveId $oppgaveId and validationresult $vr")
            }
            harUlovligKombinasjonMedReisetilskudd(smRegistreringManuell.perioder) -> {
                val vr =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName = "reisetilskuddValidation",
                                    messageForSender =
                                        "Sykmeldingen inneholder periode som kombinerer reisetilskudd med annen sykmeldingstype",
                                    messageForUser =
                                        "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                throw ValidationException("ValidationException thrown for oppgaveId $oppgaveId and validationresult $vr")
            }
            erFremtidigDato(smRegistreringManuell.behandletDato) -> {
                val vr =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName = "behandletDatoValidation",
                                    messageForSender = "Behandletdato kan ikke være frem i tid.",
                                    messageForUser =
                                        "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                throw ValidationException("ValidationException thrown for oppgaveId $oppgaveId and validationresult $vr")
            }
            studentBehandlerUtenAutorisasjon(validationResult, sykmelder) -> {
                val vr =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName =
                                        RuleHitCustomError.BEHANDLER_MANGLER_AUTORISASJON_I_HPR.name,
                                    messageForSender =
                                        "Studenter har ikke lov til å skrive sykmelding. Sykmelding må avvises.",
                                    messageForUser = "Studenter har ikke lov til å skrive sykmelding.",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                throw ValidationException("ValidationException thrown for oppgaveId $oppgaveId and validationresult $vr")
            }
            suspendertBehandler(validationResult) -> {
                val vr =
                    ValidationResult(
                        status = Status.MANUAL_PROCESSING,
                        ruleHits =
                            listOf(
                                RuleInfo(
                                    ruleName = RuleHitCustomError.BEHANDLER_SUSPENDERT.name,
                                    messageForSender =
                                        "Legen har mistet retten til å skrive sykmelding.",
                                    messageForUser = "Legen har mistet retten til å skrive sykmelding.",
                                    ruleStatus = Status.MANUAL_PROCESSING,
                                ),
                            ),
                    )
                throw ValidationException("ValidationException thrown for oppgaveId $oppgaveId and validationresult $vr")
            }
        }
    }

    fun suspendertBehandler(validationResult: ValidationResult): Boolean {
        return validationResult.ruleHits.any {
            it.ruleName == RuleHitCustomError.BEHANDLER_SUSPENDERT.name
        }
    }

    fun studentBehandlerUtenAutorisasjon(
        validationResult: ValidationResult,
        sykmelder: Sykmelder
    ): Boolean {
        val behandlerManglerAutorisasjon =
            validationResult.ruleHits.any {
                it.ruleName == RuleHitCustomError.BEHANDLER_MANGLER_AUTORISASJON_I_HPR.name
            }

        val erStudent =
            sykmelder.godkjenninger?.any {
                it.autorisasjon?.aktiv == true &&
                        it.autorisasjon.oid == HPR_GODKJENNING_KODE &&
                        it.autorisasjon.verdi == "3"
            }

        return behandlerManglerAutorisasjon && erStudent == true
    }

    fun harOverlappendePerioder(perioder: List<Periode>): Boolean {
        return harIdentiskePerioder(perioder) ||
                perioder.any { periodA ->
                    perioder
                        .filter { periodB -> periodB != periodA }
                        .any { periodB -> periodA.fom in periodB.range() || periodA.tom in periodB.range() }
                }
    }

    private fun harIdentiskePerioder(perioder: List<Periode>): Boolean {
        return perioder.distinct().count() != perioder.size
    }

    fun harUlovligKombinasjonMedReisetilskudd(perioder: List<Periode>): Boolean {
        perioder.forEach {
            if (
                it.reisetilskudd &&
                (it.aktivitetIkkeMulig != null ||
                        it.gradert != null ||
                        it.avventendeInnspillTilArbeidsgiver != null ||
                        it.behandlingsdager != null)
            ) {
                return true
            }
        }
        return false
    }

    fun Periode.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)

    fun erFremtidigDato(dato: LocalDate): Boolean {
        return dato.isAfter(LocalDate.now())
    }

}
fun List<RuleInfo>.isWhitelisted(): Boolean {
    return this.all { (ruleName) ->
        val isWhiteListed =
            enumValues<WhitelistedRuleHit>().any { enumValue -> enumValue.name == ruleName }
        isWhiteListed
    }
}

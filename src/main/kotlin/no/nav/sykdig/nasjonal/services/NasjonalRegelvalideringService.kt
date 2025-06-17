package no.nav.sykdig.nasjonal.services

import net.logstash.logback.argument.StructuredArguments
import no.nav.sykdig.nasjonal.clients.NyRegelClient
import no.nav.sykdig.shared.*
import no.nav.sykdig.nasjonal.models.RuleHitCustomError
import no.nav.sykdig.nasjonal.models.WhitelistedRuleHit
import no.nav.sykdig.nasjonal.models.SmRegistreringManuell
import no.nav.sykdig.nasjonal.models.Sykmelder
import no.nav.sykdig.shared.ReceivedSykmelding
import org.springframework.stereotype.Service
import java.time.LocalDate

const val HPR_GODKJENNING_KODE = 7704

@Service
class NasjonalRegelvalideringService(
    private val nyRegelClient: NyRegelClient,
) {
    val log = applog()
    fun validerNasjonalSykmelding(receivedSykmelding: ReceivedSykmelding, smRegistreringManuell: SmRegistreringManuell, sykmeldingId: String, loggingMeta: LoggingMeta, oppgaveId: String, sykmelder: Sykmelder): ValidationResult {
        val validationResult = nyRegelClient.valider(receivedSykmelding, sykmeldingId)
        log.info(
            "Resultat: {}, {}, {}",
            StructuredArguments.keyValue("ruleStatus", validationResult.status.name),
            StructuredArguments.keyValue(
                "ruleHits",
                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName },
            ),
            StructuredArguments.fields(loggingMeta),
        )

        return checkValidState(oppgaveId, smRegistreringManuell, sykmelder, validationResult) ?: validationResult
    }

    fun checkValidState(
        oppgaveId: String,
        smRegistreringManuell: SmRegistreringManuell,
        sykmelder: Sykmelder,
        validationResult: ValidationResult,
    ): ValidationResult? {
        when {
            smRegistreringManuell.perioder.isEmpty() -> {
                log.info("Rule hit for oppgaveId $oppgaveId and ruleName PERIODER_MANGLER")
                return ValidationResult(
                    status = Status.MANUAL_PROCESSING,
                    ruleHits =
                        listOf(
                            RuleInfo(
                                ruleName = "PERIODER_MANGLER",
                                messageForSender =
                                    "Sykmeldingen må ha minst én periode oppgitt for å være gyldig",
                                messageForUser =
                                    "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                ruleStatus = Status.MANUAL_PROCESSING,
                            ),
                        ),
                )
            }

            harOverlappendePerioder(smRegistreringManuell.perioder) -> {
                log.info("Rule hit for oppgaveId $oppgaveId and ruleName OVERLAPPENDE_PERIODER")
                return ValidationResult(
                    status = Status.MANUAL_PROCESSING,
                    ruleHits =
                        listOf(
                            RuleInfo(
                                ruleName = "OVERLAPPENDE_PERIODER",
                                messageForSender = "Sykmeldingen har overlappende perioder",
                                messageForUser =
                                    "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                ruleStatus = Status.MANUAL_PROCESSING,
                            ),
                        ),
                )
            }

            harUlovligKombinasjonMedReisetilskudd(smRegistreringManuell.perioder) -> {
                log.info("Rule hit for oppgaveId $oppgaveId and ruleName REISETILSKUDD")
                return ValidationResult(
                    status = Status.MANUAL_PROCESSING,
                    ruleHits =
                        listOf(
                            RuleInfo(
                                ruleName = "REISETILSKUDD",
                                messageForSender =
                                    "Sykmeldingen inneholder periode som kombinerer reisetilskudd med annen sykmeldingstype",
                                messageForUser =
                                    "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                ruleStatus = Status.MANUAL_PROCESSING,
                            ),
                        ),
                )
            }

            erFremtidigDato(smRegistreringManuell.behandletDato) -> {
                log.info("Rule hit for oppgaveId $oppgaveId and ruleName BEHANDLET_DATO")
                return ValidationResult(
                    status = Status.MANUAL_PROCESSING,
                    ruleHits =
                        listOf(
                            RuleInfo(
                                ruleName = "BEHANDLET_DATO",
                                messageForSender = "Behandletdato kan ikke være frem i tid.",
                                messageForUser =
                                    "Sykmelder har gjort en feil i utfyllingen av sykmeldingen.",
                                ruleStatus = Status.MANUAL_PROCESSING,
                            ),
                        ),
                )
            }

            studentBehandlerUtenAutorisasjon(validationResult, sykmelder) -> {
                log.info("Rule hit for oppgaveId $oppgaveId and ruleName BEHANDLER_MANGLER_AUTORISASJON_I_HPR")
                return ValidationResult(
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
            }

            suspendertBehandler(validationResult) -> {
                log.info("Rule hit for oppgaveId $oppgaveId and ruleName BEHANDLER_SUSPENDERT")
                return ValidationResult(
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
            }

            else -> return null
        }
    }

    fun suspendertBehandler(validationResult: ValidationResult): Boolean {
        return validationResult.ruleHits.any {
            it.ruleName == RuleHitCustomError.BEHANDLER_SUSPENDERT.name
        }
    }

    fun studentBehandlerUtenAutorisasjon(
        validationResult: ValidationResult,
        sykmelder: Sykmelder,
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

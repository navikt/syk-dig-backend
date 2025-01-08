package no.nav.sykdig.utenlandsk.services

import no.nav.sykdig.utenlandsk.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.pdl.Person
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Service
class RegelvalideringService {
    fun validerUtenlandskSykmelding(
        sykmeldt: Person,
        values: FerdistilltRegisterOppgaveValues,
    ): List<String> {
        val resultatListe = mutableListOf<String?>()
        val forsteFom = values.perioder.sortedFOMDate().firstOrNull()
        val sisteTom = values.perioder.sortedTOMDate().lastOrNull()

        resultatListe.add(sykmeldingInneholderPeriode(values.perioder))
        resultatListe.add(fomErForTom(values.perioder))
        forsteFom?.let { resultatListe.add(sykmeldingStarterForMindreEnnTreArSiden(it)) }
        sisteTom?.let {
            if (sykmeldt.fodselsdato != null) {
                resultatListe.add(sykmeldtErOver13Ar(sisteTom = it, fodselsdato = sykmeldt.fodselsdato))
            }
        }
        forsteFom?.let {
            if (sykmeldt.fodselsdato != null) {
                resultatListe.add(sykmeldtErUnder70Ar(forsteFom = it, fodselsdato = sykmeldt.fodselsdato))
            }
        }
        forsteFom?.let { resultatListe.add(sykmeldingErIkkeFremdatertMerEnn30Dager(it, values.behandletTidspunkt)) }
        if (forsteFom != null && sisteTom != null) {
            resultatListe.add(totalVarighetErUnderEtAr(forsteFom = forsteFom, sisteTom = sisteTom))
        }
        resultatListe.add(periodeneOverlapperIkke(values.perioder))
        resultatListe.add(ikkeOppholdMellomPerioder(values.perioder))
        resultatListe.add(gradertSykmeldingHarGradHoyereEnn20Prosent(values.perioder))
        resultatListe.add(gradertSykmeldingHarGradLavereEnn99Prosent(values.perioder))
        resultatListe.add(hoveddiagnoseGirRettPaSykepenger(values.hovedDiagnose))

        return resultatListe.filterNotNull()
    }

    private fun sykmeldingInneholderPeriode(perioder: List<PeriodeInput>): String? =
        if (perioder.isEmpty()) {
            "Sykmeldingen må inneholde minst én periode."
        } else {
            null
        }

    private fun fomErForTom(perioder: List<PeriodeInput>): String? =
        if (perioder.any { it.fom.isAfter(it.tom) }) {
            "Fom-dato må være før tom-dato."
        } else {
            null
        }

    private fun sykmeldingStarterForMindreEnnTreArSiden(forsteFom: LocalDate): String? =
        if (forsteFom.atStartOfDay().isBefore(LocalDate.now().minusYears(3).atStartOfDay())) {
            "Sykmeldingens fom-dato er mer enn 3 år tilbake i tid."
        } else {
            null
        }

    private fun sykmeldtErOver13Ar(
        sisteTom: LocalDate,
        fodselsdato: LocalDate,
    ): String? =
        if (sisteTom < fodselsdato.plusYears(13)) {
            "Pasienten er under 13 år. Sykmelding kan ikke benyttes."
        } else {
            null
        }

    private fun sykmeldtErUnder70Ar(
        forsteFom: LocalDate,
        fodselsdato: LocalDate,
    ): String? =
        if (forsteFom > fodselsdato.plusYears(70)) {
            "Pasienten er over 70 år. Sykmelding kan ikke benyttes."
        } else {
            null
        }

    private fun sykmeldingErIkkeFremdatertMerEnn30Dager(
        forsteFom: LocalDate,
        behandletTidspunkt: OffsetDateTime,
    ): String? =
        if (forsteFom > behandletTidspunkt.plusDays(30).toLocalDate()) {
            "Sykmeldingen er fremdatert mer enn 30 dager."
        } else {
            null
        }

    private fun totalVarighetErUnderEtAr(
        forsteFom: LocalDate,
        sisteTom: LocalDate,
    ): String? =
        if ((forsteFom..sisteTom).daysBetween() > 365) {
            "Sykmeldingen kan ikke ha en varighet på over ett år."
        } else {
            null
        }

    private fun periodeneOverlapperIkke(perioder: List<PeriodeInput>): String? {
        val overlapper =
            perioder.any { periodA ->
                perioder
                    .filter { periodB -> periodB != periodA }
                    .any { periodB ->
                        periodA.fom in periodB.range() || periodA.tom in periodB.range()
                    }
            }
        return if (overlapper) {
            "Periodene må ikke overlappe hverandre."
        } else {
            null
        }
    }

    private fun ikkeOppholdMellomPerioder(perioder: List<PeriodeInput>): String? {
        val periodeRanges =
            perioder
                .sortedBy { it.fom }
                .map { it.fom to it.tom }
        var gapBetweenPeriods = false
        for (i in 1 until periodeRanges.size) {
            gapBetweenPeriods =
                workdaysBetween(periodeRanges[i - 1].second, periodeRanges[i].first) > 0
            if (gapBetweenPeriods) {
                break
            }
        }
        return if (gapBetweenPeriods) {
            "Det kan ikke være opphold mellom sykmeldingsperiodene."
        } else {
            null
        }
    }

    private fun gradertSykmeldingHarGradHoyereEnn20Prosent(perioder: List<PeriodeInput>): String? =
        if (perioder.any { it.grad != null && it.grad < 20 }) {
            "Sykmeldingsgraden kan ikke være mindre enn 20 %."
        } else {
            null
        }

    private fun gradertSykmeldingHarGradLavereEnn99Prosent(perioder: List<PeriodeInput>): String? =
        if (perioder.any { it.grad != null && it.grad > 99 }) {
            "Sykmeldingsgraden kan ikke være mer enn 99% når det er en gradert sykmelding."
        } else {
            null
        }

    private fun hoveddiagnoseGirRettPaSykepenger(hoveddiagnose: DiagnoseInput): String? =
        if (hoveddiagnose.system == "ICPC2" && hoveddiagnose.kode.startsWith("Z")) {
            "Angitt hoveddiagnose (z-diagnose) gir ikke rett til sykepenger."
        } else {
            null
        }
}

fun List<PeriodeInput>.sortedFOMDate(): List<LocalDate> = map { it.fom }.sorted()

fun List<PeriodeInput>.sortedTOMDate(): List<LocalDate> = map { it.tom }.sorted()

fun PeriodeInput.range(): ClosedRange<LocalDate> = fom.rangeTo(tom)

fun ClosedRange<LocalDate>.daysBetween(): Long = ChronoUnit.DAYS.between(start, endInclusive)

fun workdaysBetween(
    a: LocalDate,
    b: LocalDate,
): Int =
    (1 until ChronoUnit.DAYS.between(a, b))
        .map { a.plusDays(it) }.count { it.dayOfWeek !in arrayOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }

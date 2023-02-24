package no.nav.sykdig.digitalisering.regelvalidering

import no.nav.sykdig.digitalisering.model.FerdistilltRegisterOppgaveValues
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class RegelvalideringServiceTest {
    private val regelvalideringService = RegelvalideringService()

    private val sykmeldt = Person(
        fnr = "12345678910",
        navn = Navn("Fornavn", null, "Etternavn"),
        aktorId = "aktorid",
        bostedsadresse = null,
        oppholdsadresse = null,
        fodselsdato = LocalDate.now().minusYears(40),
    )
    private val happyCaseValues = FerdistilltRegisterOppgaveValues(
        fnrPasient = "12345678910",
        behandletTidspunkt = OffsetDateTime.now(ZoneOffset.UTC),
        skrevetLand = "SWE",
        perioder = listOf(
            PeriodeInput(
                type = PeriodeType.AKTIVITET_IKKE_MULIG,
                fom = LocalDate.now().minusWeeks(2),
                tom = LocalDate.now().minusWeeks(1),
            ),
        ),
        hovedDiagnose = DiagnoseInput("A000", "ICD10"),
        biDiagnoser = emptyList(),
        harAndreRelevanteOpplysninger = false,
    )

    @Test
    fun happyCase() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(sykmeldt, happyCaseValues)

        assertEquals(valideringsresultat.size, 0)
    }

    @Test
    fun sykmeldingManglerPeriode() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(perioder = emptyList()),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Sykmeldingen må inneholde minst én periode.")
    }

    @Test
    fun fomErEtterTom() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.now().minusWeeks(1),
                        tom = LocalDate.now().minusWeeks(2),
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Fom-dato må være før tom-dato.")
    }

    @Test
    fun fomErMerEnnTreArSiden() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.now().minusYears(3).minusDays(5),
                        tom = LocalDate.now().minusYears(3).plusDays(5),
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Sykmeldingens fom-dato er mer enn 3 år tilbake i tid.")
    }

    @Test
    fun sykmeldtErUnder13Ar() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt.copy(fodselsdato = LocalDate.now().minusYears(12)),
            happyCaseValues,
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Pasienten er under 13 år. Sykmelding kan ikke benyttes.")
    }

    @Test
    fun sykmeldtErOver70Ar() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt.copy(fodselsdato = LocalDate.now().minusYears(71)),
            happyCaseValues,
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Pasienten er over 70 år. Sykmelding kan ikke benyttes.")
    }

    @Test
    fun fremdatertMerEnn30Dager() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.now().plusDays(31),
                        tom = LocalDate.now().plusDays(45),
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Sykmeldingen er fremdatert mer enn 30 dager.")
    }

    @Test
    fun varighetOverEtAr() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.now().minusMonths(6),
                        tom = LocalDate.now(),
                    ),
                    PeriodeInput(
                        type = PeriodeType.GRADERT,
                        fom = LocalDate.now().plusDays(1),
                        tom = LocalDate.now().plusMonths(8),
                        grad = 50,
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Sykmeldingen kan ikke ha en varighet på over ett år.")
    }

    @Test
    fun overlappendePerioder() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.now().minusMonths(1),
                        tom = LocalDate.now(),
                    ),
                    PeriodeInput(
                        type = PeriodeType.GRADERT,
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(15),
                        grad = 50,
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Periodene må ikke overlappe hverandre.")
    }

    @Test
    fun oppholdMellomPerioder() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.now().minusMonths(1),
                        tom = LocalDate.now(),
                    ),
                    PeriodeInput(
                        type = PeriodeType.GRADERT,
                        fom = LocalDate.now().plusDays(4),
                        tom = LocalDate.now().plusDays(15),
                        grad = 50,
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Det kan ikke være opphold mellom sykmeldingsperiodene.")
    }

    @Test
    fun gradUnder20Prosent() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.GRADERT,
                        fom = LocalDate.now().plusDays(4),
                        tom = LocalDate.now().plusDays(15),
                        grad = 19,
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Sykmeldingsgraden kan ikke være mindre enn 20 %.")
    }

    @Test
    fun gradOver99Prosent() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.GRADERT,
                        fom = LocalDate.now().plusDays(4),
                        tom = LocalDate.now().plusDays(15),
                        grad = 100,
                    ),
                ),
            ),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(
            valideringsresultat[0],
            "Sykmeldingsgraden kan ikke være mer enn 99% når det er en gradert sykmelding.",
        )
    }

    @Test
    fun hoveddiagnoseGirIkkeRettPaSykepenger() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(hovedDiagnose = DiagnoseInput("Z09", "ICPC2")),
        )

        assertEquals(valideringsresultat.size, 1)
        assertEquals(valideringsresultat[0], "Angitt hoveddiagnose (z-diagnose) gir ikke rett til sykepenger.")
    }

    @Test
    fun zDiagnoseOgOverlappendePerioder() {
        val valideringsresultat = regelvalideringService.validerUtenlandskSykmelding(
            sykmeldt,
            happyCaseValues.copy(
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.now().minusMonths(1),
                        tom = LocalDate.now(),
                    ),
                    PeriodeInput(
                        type = PeriodeType.GRADERT,
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusDays(15),
                        grad = 50,
                    ),
                ),
                hovedDiagnose = DiagnoseInput("Z09", "ICPC2"),
            ),
        )

        assertEquals(valideringsresultat.size, 2)
        assertEquals(valideringsresultat[0], "Periodene må ikke overlappe hverandre.")
        assertEquals(valideringsresultat[1], "Angitt hoveddiagnose (z-diagnose) gir ikke rett til sykepenger.")
    }
}

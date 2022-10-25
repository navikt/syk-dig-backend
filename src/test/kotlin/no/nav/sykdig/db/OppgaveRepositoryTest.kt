package no.nav.sykdig.db

import no.nav.sykdig.FellesTestOppsett
import no.nav.sykdig.digitalisering.createDigitalseringsoppgaveDbModel
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.generated.types.SykmeldingUnderArbeidValues
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime

@Transactional
class OppgaveRepositoryTest : FellesTestOppsett() {

    @BeforeEach
    fun before() {
        oppgaveRepository.lagreOppgave(
            createDigitalseringsoppgaveDbModel(
                oppgaveId = "345",
                fnr = "saksbehandler-fnr",
            )
        )
    }

    @Test
    fun `should get oppgave`() {
        val oppgave = oppgaveRepository.getOppgave("345")

        "345" shouldBeEqualTo oppgave?.oppgaveId
    }

    @Test
    fun `should update oppgave`() {
        oppgaveRepository.updateOppgave(
            oppgaveId = "345",
            values = SykmeldingUnderArbeidValues(
                fnrPasient = "nytt-fnr-pasient",
                behandletTidspunkt = LocalDate.parse("2020-01-01"),
                skrevetLand = "ZMB",
                perioder = listOf(
                    PeriodeInput(
                        type = PeriodeType.AKTIVITET_IKKE_MULIG,
                        fom = LocalDate.parse("2020-01-01"),
                        tom = LocalDate.parse("2020-01-15"),
                    ),
                    PeriodeInput(
                        type = PeriodeType.GRADERT,
                        fom = LocalDate.parse("2021-01-01"),
                        tom = LocalDate.parse("2021-01-15"),
                        grad = 68,
                    )
                ),
                hovedDiagnose = DiagnoseInput(kode = "Z00", system = "ICPC-2"),
                biDiagnoser = listOf(
                    DiagnoseInput(kode = "Z01", system = "ICPC-22"),
                    DiagnoseInput(kode = "Z02", system = "ICPC-23"),
                ),
                harAndreRelevanteOpplysninger = true,
            ),
            ident = "fake-test-ident",
            false,
        )
        val oppgave = oppgaveRepository.getOppgave("345")

        oppgave?.fnr shouldBeEqualTo "saksbehandler-fnr"
        oppgave?.endretAv shouldBeEqualTo "fake-test-ident"
        oppgave?.ferdigstilt shouldBeEqualTo null
        oppgave?.sykmelding?.fnrPasient shouldBeEqualTo "nytt-fnr-pasient"
        oppgave?.sykmelding?.sykmelding?.behandletTidspunkt shouldBeEqualTo OffsetDateTime.parse("2020-01-01T12:00:00Z")
        oppgave?.sykmelding?.utenlandskSykmelding?.land shouldBeEqualTo "ZMB"
        oppgave?.sykmelding?.utenlandskSykmelding?.andreRelevanteOpplysninger shouldBeEqualTo true
        oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.kode shouldBeEqualTo "Z00"
        oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.system shouldBeEqualTo "ICPC-2"
        oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(0)?.kode shouldBeEqualTo "Z01"
        oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(0)?.system shouldBeEqualTo "ICPC-22"
        oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(1)?.kode shouldBeEqualTo "Z02"
        oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(1)?.system shouldBeEqualTo "ICPC-23"
        oppgave?.sykmelding?.sykmelding?.perioder?.get(0)?.fom shouldBeEqualTo LocalDate.parse("2020-01-01")
        oppgave?.sykmelding?.sykmelding?.perioder?.get(0)?.tom shouldBeEqualTo LocalDate.parse("2020-01-15")
        oppgave?.sykmelding?.sykmelding?.perioder?.get(1)?.fom shouldBeEqualTo LocalDate.parse("2021-01-01")
        oppgave?.sykmelding?.sykmelding?.perioder?.get(1)?.tom shouldBeEqualTo LocalDate.parse("2021-01-15")
        oppgave?.sykmelding?.sykmelding?.perioder?.get(1)?.gradert?.grad shouldBeEqualTo 68
    }
}

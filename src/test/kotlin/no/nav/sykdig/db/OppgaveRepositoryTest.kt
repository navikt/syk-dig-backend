package no.nav.sykdig.db

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.digitalisering.createDigitalseringsoppgaveDbModel
import no.nav.sykdig.utenlandsk.models.UferdigRegisterOppgaveValues
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.utenlandsk.db.toSykmelding
import no.nav.sykdig.shared.utils.toOffsetDateTimeAtNoon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime

@Transactional
class OppgaveRepositoryTest : IntegrationTest() {
    @BeforeEach
    fun before() {
        oppgaveRepository.lagreOppgave(
            createDigitalseringsoppgaveDbModel(
                oppgaveId = "345",
                fnr = "sykmeldt-fnr",
            ),
        )
    }

    @Test
    fun `should get oppgave`() {
        val oppgave = oppgaveRepository.getOppgave("345")

        assertEquals("345", oppgave?.oppgaveId)
    }

    @Test
    fun `should update oppgave`() {
        val preOppgave = oppgaveRepository.getOppgave("345")!!
        oppgaveRepository.updateOppgave(
            oppgave = preOppgave,
            sykmelding =
                toSykmelding(
                    preOppgave,
                    UferdigRegisterOppgaveValues(
                        fnrPasient = "nytt-fnr-pasient",
                        behandletTidspunkt = LocalDate.parse("2020-01-01").toOffsetDateTimeAtNoon(),
                        skrevetLand = "ZMB",
                        perioder =
                            listOf(
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
                                ),
                            ),
                        hovedDiagnose = DiagnoseInput(kode = "Z01", system = "ICPC2"),
                        biDiagnoser =
                            listOf(
                                DiagnoseInput(kode = "Z02", system = "ICPC2"),
                                DiagnoseInput(kode = "Z03", system = "ICPC2"),
                            ),
                        folkeRegistertAdresseErBrakkeEllerTilsvarende = false,
                        erAdresseUtland = null,
                    ),
                ),
            navEpost = "fake-test-ident",
            false,
        )
        val oppgave = oppgaveRepository.getOppgave("345")

        assertEquals("sykmeldt-fnr", oppgave?.fnr)
        assertEquals("fake-test-ident", oppgave?.endretAv)
        assertEquals(null, oppgave?.ferdigstilt)
        assertEquals("nytt-fnr-pasient", oppgave?.sykmelding?.fnrPasient)
        assertEquals(OffsetDateTime.parse("2020-01-01T12:00:00Z"), oppgave?.sykmelding?.sykmelding?.behandletTidspunkt)
        assertEquals("ZMB", oppgave?.sykmelding?.utenlandskSykmelding?.land)
        assertEquals("Z01", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.kode)
        assertEquals("ICPC2", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.system)
        assertEquals("Problem økonomi/fattigdom", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.hovedDiagnose?.tekst)
        assertEquals("Z02", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(0)?.kode)
        assertEquals("ICPC2", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(0)?.system)
        assertEquals("Problem drikkevann/mat", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(0)?.tekst)
        assertEquals("Z03", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(1)?.kode)
        assertEquals("ICPC2", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(1)?.system)
        assertEquals("Problem bolig/nabolag", oppgave?.sykmelding?.sykmelding?.medisinskVurdering?.biDiagnoser?.get(1)?.tekst)
        assertEquals(LocalDate.parse("2020-01-01"), oppgave?.sykmelding?.sykmelding?.perioder?.get(0)?.fom)
        assertEquals(LocalDate.parse("2020-01-15"), oppgave?.sykmelding?.sykmelding?.perioder?.get(0)?.tom)
        assertEquals(LocalDate.parse("2021-01-01"), oppgave?.sykmelding?.sykmelding?.perioder?.get(1)?.fom)
        assertEquals(LocalDate.parse("2021-01-15"), oppgave?.sykmelding?.sykmelding?.perioder?.get(1)?.tom)
        assertEquals(68, oppgave?.sykmelding?.sykmelding?.perioder?.get(1)?.gradert?.grad)
    }

    @Test
    fun `should ferdigstill oppgave and update sykmelding`() {
        val saksbehandlerIdent = "Z212313"

        val preOppgave = oppgaveRepository.getOppgave("345")!!

        oppgaveRepository.ferdigstillOppgaveGosys(preOppgave, saksbehandlerIdent, null)

        val oppgave = oppgaveRepository.getOppgave("345")

        assertEquals("sykmeldt-fnr", oppgave?.fnr)
        assertEquals("Z212313", oppgave?.endretAv)
        assertEquals(LocalDate.now(), oppgave?.ferdigstilt?.toLocalDate())
        assertEquals(null, oppgave?.sykmelding)
    }
}

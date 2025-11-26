package no.nav.sykdig.nasjonal.mapping

import java.util.*
import no.nav.sykdig.digitalisering.papirsykmelding.mapToDaoOppgave
import no.nav.sykdig.digitalisering.papirsykmelding.mapToMedisinskVurdering
import no.nav.sykdig.nasjonal.util.testDataPapirManuellOppgave
import no.nav.sykdig.shared.Diagnose
import no.nav.sykdig.shared.MedisinskVurdering
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NasjonalMappingTest {

    @Test
    fun `mapToDao der id er null`() {
        val dao = mapToDaoOppgave(testDataPapirManuellOppgave(123), null)

        assertEquals("123", dao.sykmeldingId)
        assertEquals(null, dao.id)
    }

    @Test
    fun `mapToDao der id ikke er null`() {
        val uuid = UUID.randomUUID()
        val dao = mapToDaoOppgave(testDataPapirManuellOppgave(123), uuid)

        assertEquals("123", dao.sykmeldingId)
        assertEquals(uuid, dao.id)
    }

    @Test
    fun `Map ok diagnose`() {
        val medisinskVurdering =
            MedisinskVurdering(
                hovedDiagnose = Diagnose("ICPC2", "L84", "tekst"),
                biDiagnoser = emptyList(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
            )
        val mappedMedisinskVurdering = mapToMedisinskVurdering(medisinskVurdering)
        mappedMedisinskVurdering.hovedDiagnose?.kode shouldBeEqualTo "L84"
        mappedMedisinskVurdering.hovedDiagnose?.tekst shouldBeEqualTo "tekst"
    }

    @Test
    fun `Map invalid diagnose`() {
        val medisinskVurdering =
            MedisinskVurdering(
                hovedDiagnose = Diagnose("ICPC2", "L8400", "tekst"),
                biDiagnoser = emptyList(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
            )
        val mappedMedisinskVurdering = mapToMedisinskVurdering(medisinskVurdering)
        mappedMedisinskVurdering.hovedDiagnose?.kode shouldBeEqualTo null
        mappedMedisinskVurdering.hovedDiagnose?.tekst shouldBeEqualTo null
    }

    @Test
    fun `Map ok bidiagnose`() {
        val medisinskVurdering =
            MedisinskVurdering(
                hovedDiagnose = null,
                biDiagnoser =
                    listOf(Diagnose("ICPC2", "L84", "tekst"), Diagnose("ICPC2", "L83", "tekst2")),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
            )
        val mappedMedisinskVurdering = mapToMedisinskVurdering(medisinskVurdering)
        mappedMedisinskVurdering.biDiagnoser.first().kode shouldBeEqualTo "L84"
        mappedMedisinskVurdering.biDiagnoser.first().tekst shouldBeEqualTo "tekst"
        mappedMedisinskVurdering.biDiagnoser.last().kode shouldBeEqualTo "L83"
        mappedMedisinskVurdering.biDiagnoser.last().tekst shouldBeEqualTo "tekst2"
    }

    @Test
    fun `Map invalid bidiagnose`() {
        val medisinskVurdering =
            MedisinskVurdering(
                hovedDiagnose = null,
                biDiagnoser =
                    listOf(
                        Diagnose("ICPC2", "L8400", "tekst"),
                        Diagnose("ICPC2", "L8300", "tekst2"),
                    ),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null,
            )
        val mappedMedisinskVurdering = mapToMedisinskVurdering(medisinskVurdering)
        mappedMedisinskVurdering.biDiagnoser.first().kode shouldBeEqualTo null
        mappedMedisinskVurdering.biDiagnoser.first().tekst shouldBeEqualTo null
        mappedMedisinskVurdering.biDiagnoser.last().kode shouldBeEqualTo null
        mappedMedisinskVurdering.biDiagnoser.last().tekst shouldBeEqualTo null
    }
}

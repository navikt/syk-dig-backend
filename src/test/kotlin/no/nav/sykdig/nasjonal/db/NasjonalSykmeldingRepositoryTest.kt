package no.nav.sykdig.nasjonal.db

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.shared.AktivitetIkkeMulig
import no.nav.sykdig.shared.ArbeidsrelatertArsak
import no.nav.sykdig.shared.ArbeidsrelatertArsakType
import no.nav.sykdig.shared.Periode
import no.nav.sykdig.nasjonal.db.models.NasjonalSykmeldingDAO
import no.nav.sykdig.nasjonal.util.getReceivedSykmelding
import no.nav.sykdig.shared.ReceivedSykmelding
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class NasjonalSykmeldingRepositoryTest : IntegrationTest() {

    @BeforeEach
    fun setup() {
        nasjonalSykmeldingRepository.deleteAll()
    }

    @Test
    fun `legg til og hent ny sykmelding`() {
        val dao = testData(null, "123")
        println("Sykmelding før lagring: ${dao.sykmelding}")
        println("Sykmelding JSON: ${ObjectMapper().writeValueAsString(dao.sykmelding)}")
        Assertions.assertNotNull(dao.sykmelding)
        val res = nasjonalSykmeldingRepository.save(dao)

        val nasjonalSykmelding = nasjonalSykmeldingRepository.findBySykmeldingId(res.sykmeldingId)
        assertEquals(1, nasjonalSykmelding.count())
    }

    @Test
    fun `slett en sykmelding fra db`() {
        val dao = testData(null, "123")
        nasjonalSykmeldingRepository.save(dao)
        val antall = nasjonalSykmeldingRepository.deleteBySykmeldingId("123")
        assertEquals(1, antall)
        assertEquals(0, nasjonalSykmeldingRepository.findBySykmeldingId("123").size)
    }

    @Test
    fun `slett flere sykmeldinger med samme sykmeldingId fra db`() {
        val dao1 = testData(null, "123")
        val dao2 = testData(null, "123")
        nasjonalSykmeldingRepository.save(dao1)
        nasjonalSykmeldingRepository.save(dao2)
        val antall = nasjonalSykmeldingRepository.deleteBySykmeldingId("123")
        assertEquals(2, antall)
        assertEquals(0, nasjonalSykmeldingRepository.findBySykmeldingId("123").size)
    }

    fun testData(id: UUID?, sykmeldingId: String): NasjonalSykmeldingDAO {
        return NasjonalSykmeldingDAO(
            id = id,
            sykmeldingId = sykmeldingId,
            sykmelding = sykmeldingTestData(sykmeldingId),
            timestamp = OffsetDateTime.now(),
            ferdigstiltAv = "the saksbehandler",
            datoFerdigstilt = OffsetDateTime.now()
        )
    }

    fun sykmeldingTestData(sykmeldingId: String): ReceivedSykmelding {
        return getReceivedSykmelding(fnrPasient = "123", sykmelderFnr = "456", sykmeldingId = sykmeldingId)
    }

    fun perioderTestData(): List<Periode> {
        return listOf(
            Periode(
            fom = LocalDate.now(),
            tom = LocalDate.now().plusDays(3),
            aktivitetIkkeMulig = AktivitetIkkeMulig(
                medisinskArsak = null,
                arbeidsrelatertArsak = ArbeidsrelatertArsak(
                    beskrivelse = "Datt i trappa",
                    arsak = listOf(ArbeidsrelatertArsakType.MANGLENDE_TILRETTELEGGING)
                )
            ),
            avventendeInnspillTilArbeidsgiver = null,
            behandlingsdager = null,
            gradert = null,
            reisetilskudd = false
        )
        )
    }
}
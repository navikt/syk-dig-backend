package no.nav.sykdig.nasjonal.db

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.shared.Adresse
import no.nav.sykdig.shared.AktivitetIkkeMulig
import no.nav.sykdig.shared.Arbeidsgiver
import no.nav.sykdig.shared.ArbeidsrelatertArsak
import no.nav.sykdig.shared.ArbeidsrelatertArsakType
import no.nav.sykdig.shared.AvsenderSystem
import no.nav.sykdig.shared.Behandler
import no.nav.sykdig.shared.Diagnose
import no.nav.sykdig.shared.HarArbeidsgiver
import no.nav.sykdig.shared.KontaktMedPasient
import no.nav.sykdig.shared.MedisinskVurdering
import no.nav.sykdig.shared.Periode
import no.nav.sykdig.shared.Sykmelding
import no.nav.sykdig.nasjonal.db.models.NasjonalSykmeldingDAO
import org.amshove.kluent.internal.assertEquals
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
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
        println(dao.sykmelding)
        Assertions.assertNotNull(dao.sykmelding)
        val res = nasjonalSykmeldingRepository.save(dao)

        val nasjonalSykmelding = nasjonalSykmeldingRepository.findBySykmeldingId(res.sykmeldingId)
        assertEquals(1, nasjonalSykmelding.count())
    }

    @Test
    fun `slett en sykmelding fra db`() {
        val dao = testData(null, "123")
        nasjonalSykmeldingRepository.save(dao)
        val id = nasjonalSykmeldingRepository.findBySykmeldingId("123")
        id.forEach {
            it.id?.let { id -> nasjonalSykmeldingRepository.deleteById(id) }
        }
        assertEquals(0, nasjonalSykmeldingRepository.findBySykmeldingId("123").count())
    }

    @Test
    fun `slett flere sykmeldinger med samme sykmeldingId fra db`() {
        val dao1 = testData(null, "123")
        val dao2 = testData(null, "123")
        nasjonalSykmeldingRepository.save(dao1)
        nasjonalSykmeldingRepository.save(dao2)
        val id = nasjonalSykmeldingRepository.findBySykmeldingId("123")
        assertEquals(2, id.count())
        id.forEach {
            it.id?.let { id -> nasjonalSykmeldingRepository.deleteById(id) }
        }
        assertEquals(0, nasjonalSykmeldingRepository.findBySykmeldingId("123").count())
    }

    fun testData(id: UUID?, sykmeldingId: String): NasjonalSykmeldingDAO {
        return NasjonalSykmeldingDAO(
            id = id,
            sykmeldingId = sykmeldingId,
            sykmelding = sykmeldingTestData(sykmeldingId) ,
            timestamp = OffsetDateTime.now(),
            ferdigstiltAv = "the saksbehandler",
            datoFerdigstilt = LocalDateTime.now()
        )
    }

    fun sykmeldingTestData(sykmeldingId: String): Sykmelding {
        return Sykmelding(
            id = sykmeldingId,
            msgId = sykmeldingId,
            pasientAktoerId = "123",
            medisinskVurdering = MedisinskVurdering(
                hovedDiagnose = Diagnose(
                    system = "foo",
                    kode = "bar",
                    tekst = null
                ),
                biDiagnoser = emptyList(),
                svangerskap = false,
                yrkesskade = false,
                yrkesskadeDato = null,
                annenFraversArsak = null
            ),
            skjermesForPasient = false,
            arbeidsgiver = Arbeidsgiver(
                harArbeidsgiver = HarArbeidsgiver.EN_ARBEIDSGIVER,
                navn = null,
                yrkesbetegnelse = null,
                stillingsprosent = null
            ),
            perioder = perioderTestData(),
            prognose = null,
            utdypendeOpplysninger = emptyMap(),
            tiltakArbeidsplassen = null,
            tiltakNAV = null,
            andreTiltak = null,
            meldingTilNAV = null,
            meldingTilArbeidsgiver = null,
            kontaktMedPasient = KontaktMedPasient(
                kontaktDato = null,
                begrunnelseIkkeKontakt = null
            ),
            behandletTidspunkt = LocalDateTime.now(),
            behandler = Behandler(
                fornavn = "fornavn",
                mellomnavn = null,
                etternavn = "etternavn",
                aktoerId = "456456456",
                fnr = "98765432101",
                hpr = null,
                her = null,
                adresse = Adresse(
                    gate = null,
                    postnummer = null,
                    kommune = null,
                    postboks = null,
                    land = null
                ),
                tlf = null
            ),
            avsenderSystem = AvsenderSystem(
                navn = "avsenderSystem",
                versjon = "1.0"
            ),
            syketilfelleStartDato = LocalDate.now(),
            signaturDato = LocalDateTime.now(),
            navnFastlege = "fastlegen"
        )
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
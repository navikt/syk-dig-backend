package no.nav.sykdig.digitalisering.papirsykmelding

import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.digitalisering.felles.Adresse
import no.nav.sykdig.digitalisering.felles.AktivitetIkkeMulig
import no.nav.sykdig.digitalisering.felles.Arbeidsgiver
import no.nav.sykdig.digitalisering.felles.ArbeidsrelatertArsak
import no.nav.sykdig.digitalisering.felles.ArbeidsrelatertArsakType
import no.nav.sykdig.digitalisering.felles.AvsenderSystem
import no.nav.sykdig.digitalisering.felles.Behandler
import no.nav.sykdig.digitalisering.felles.Diagnose
import no.nav.sykdig.digitalisering.felles.HarArbeidsgiver
import no.nav.sykdig.digitalisering.felles.KontaktMedPasient
import no.nav.sykdig.digitalisering.felles.MedisinskVurdering
import no.nav.sykdig.digitalisering.felles.Periode
import no.nav.sykdig.digitalisering.felles.Sykmelding
import no.nav.sykdig.digitalisering.papirsykmelding.db.model.NasjonalSykmeldingDAO
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

class NasjonalSykmeldingRepositoryTest : IntegrationTest() {

//    @AfterEach
//    fun setup() {
//        nasjonalSykmeldingRepository.deleteAll()
//    }

    @Test
    fun `legg til og hent ny sykmelding`() {
        val dao = testData(null, "123")
        println(dao.sykmelding)
        Assertions.assertNotNull(dao.sykmelding)
        val res = nasjonalSykmeldingRepository.save(dao)

        val nasjonalSykmelding = nasjonalSykmeldingRepository.findBySykmeldingId(res.sykmeldingId)
        Assertions.assertTrue(nasjonalSykmelding.isPresent)
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
        return listOf(Periode(
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
        ))
    }
}
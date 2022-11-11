package no.nav.sykdig.digitalisering

import java.time.LocalDate
import java.time.Month
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.syfo.model.Adresse

import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.Diagnose
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.UtenlandskSykmelding
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.db.OppgaveRepository
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.pdl.Bostedsadresse
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.tilgangskontroll.SyfoTilgangskontrollOboClient
import no.nav.sykdig.generated.types.DiagnoseInput
import no.nav.sykdig.generated.types.PeriodeInput
import no.nav.sykdig.generated.types.PeriodeType
import no.nav.sykdig.model.DigitaliseringsoppgaveDbModel
import no.nav.sykdig.model.Sykmelding
import no.nav.sykdig.model.SykmeldingUnderArbeid
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMetrics
@SpringBootTest(classes = [SykDigBackendApplication::class])
class OppgaveServiceTest {
    @Autowired
    lateinit var oppgaveRepository: OppgaveRepository

    @Autowired
    lateinit var ferdigstillingService: FerdigstillingService

    @Autowired
    lateinit var syfoTilgangskontrollClient: SyfoTilgangskontrollOboClient

    @Test
    fun `map to receivedSykmelding`() {

        val oppgaveService = OppgaveService(oppgaveRepository, ferdigstillingService, syfoTilgangskontrollClient)

        val validatedValues = ValidatedOppgaveValues(
            fnrPasient = "12345678910",
            behandletTidspunkt = OffsetDateTime.parse("2022-10-26T12:00:00Z"),
            skrevetLand = "POL",
            perioder =  listOf(PeriodeInput(
                type = PeriodeType.AKTIVITET_IKKE_MULIG,
                fom = LocalDate.of(2019, Month.AUGUST, 15),
                tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
               grad = 100
            )),
            hovedDiagnose = DiagnoseInput(kode = "A070", system = "2.16.578.1.12.4.1.1.7170"),
            biDiagnoser = emptyList(),
        )
        val oppgave = DigitaliseringsoppgaveDbModel(
            oppgaveId = "123",
            fnr = "12345678910",
            journalpostId = "journalPostId",
            dokumentInfoId = null,
            opprettet = OffsetDateTime.now(),
            ferdigstilt = null,
            sykmeldingId = UUID.randomUUID(),
            type = "type",
            sykmelding = SykmeldingUnderArbeid(
                sykmelding = Sykmelding(
                    id = "1213",
                    msgId = "1553--213-12-123",
                    medisinskVurdering = MedisinskVurdering(
                        hovedDiagnose = Diagnose(
                            system = "2.16.578.1.12.4.1.1.7170",
                            kode = "A070",
                            tekst = "Balantidiasis Dysenteri som skyldes Balantidium"
                        ),
                        biDiagnoser = listOf(),
                        svangerskap = false,
                        yrkesskade = false,
                        yrkesskadeDato = null,
                        annenFraversArsak = null
                    ),
                    arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, "NAV ikt", "Utvikler", 100),
                    perioder = listOf(
                        Periode(
                            fom = LocalDate.of(2019, Month.AUGUST, 15),
                            tom = LocalDate.of(2019, Month.SEPTEMBER, 30),
                            aktivitetIkkeMulig = AktivitetIkkeMulig(
                                medisinskArsak = null,
                                arbeidsrelatertArsak = null
                            ),
                            avventendeInnspillTilArbeidsgiver = null,
                            behandlingsdager = null,
                            gradert = null,
                            reisetilskudd = false
                        )
                    ),
                    prognose = null,
                    utdypendeOpplysninger = emptyMap(),
                    tiltakArbeidsplassen = null,
                    tiltakNAV = null,
                    andreTiltak = null,
                    meldingTilNAV = null,
                    meldingTilArbeidsgiver = null,
                    kontaktMedPasient = null,
                    behandletTidspunkt = OffsetDateTime.now(),
                    behandler = Behandler("Per", "", "Person", "123", "", "", "", Adresse(null, null, null, null, null), ""),
                    syketilfelleStartDato = null
                ),
                fnrPasient = "12345678910",
                fnrLege = "231555533",
                legeHprNr = null,
                navLogId = UUID.randomUUID().toString(),
                msgId = UUID.randomUUID().toString(),
                legekontorOrgNr = null,
                legekontorHerId = null,
                legekontorOrgName = null,
                mottattDato = null,
                utenlandskSykmelding = UtenlandskSykmelding(
                    land = "POL",
                    andreRelevanteOpplysninger = false
                )

            ),
            endretAv = "test testesen",
            timestamp = OffsetDateTime.now()
        )
        val person = Person(
            "12345678910",
            Navn("fornavn", null, "etternavn"),
            Bostedsadresse(
                null,
                null,
                null,
                null,
                null,
            ),
            null,
        )

        val harAndreRelevanteOpplysninger = false

        val receivedSykmelding =
            oppgaveService.mapToReceivedSykmelding(validatedValues, oppgave, person, harAndreRelevanteOpplysninger)

        receivedSykmelding.personNrPasient shouldBeEqualTo "12345678910"

    }
}
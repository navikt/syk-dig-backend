package no.nav.sykdig.digitalisering

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.digitalisering.ferdigstilling.FerdigstillingService
import no.nav.sykdig.digitalisering.ferdigstilling.GosysService
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.GetOppgaveResponse
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.OppgaveType
import no.nav.sykdig.digitalisering.ferdigstilling.oppgave.Oppgavestatus
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.PersonService
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.metrics.MetricRegister
import no.nav.sykdig.model.OppgaveDbModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [SykDigBackendApplication::class])
class DigitaliseringsoppgaveServiceTest : IntegrationTest() {
    @Autowired
    lateinit var sykDigOppgaveService: SykDigOppgaveService

    @MockBean
    lateinit var gosysService: GosysService

    @MockBean
    lateinit var personService: PersonService

    @MockBean
    lateinit var metricRegister: MetricRegister

    @Autowired
    lateinit var digitaliseringsoppgaveService: DigitaliseringsoppgaveService

    @MockBean
    lateinit var ferdigstillingService: FerdigstillingService

    val sykmeldingId = UUID.randomUUID()

    val oppgaveId = "1"

    val oppgaveMock =
        OppgaveDbModel(
            oppgaveId = oppgaveId,
            fnr = "20086600138",
            journalpostId = "2",
            dokumentInfoId = "131",
            opprettet = OffsetDateTime.now(),
            ferdigstilt = null,
            avvisingsgrunn = null,
            tilbakeTilGosys = false,
            sykmeldingId = sykmeldingId,
            type = "UTLAND",
            sykmelding = null,
            endretAv = "Z123456",
            dokumenter = emptyList(),
            timestamp = OffsetDateTime.now(),
            source = "scanning",
        )

    val oppgaveResponseMock =
        GetOppgaveResponse(
            versjon = 1,
            status = Oppgavestatus.OPPRETTET,
            behandlesAvApplikasjon = "SMM",
            tilordnetRessurs = "A123456",
            beskrivelse = "Dette var ikkje bra",
            oppgavetype = OppgaveType.BEH_SED,
            tildeltEnhetsnr = "0393",
            aktivDato = LocalDate.now(),
            duplikat = false,
        )

    val excpetedAvvisingsgrunn = Avvisingsgrunn.MANGLENDE_DIAGNOSE

    @Test
    fun testAvvisOk() {
        val oppgave = oppgaveMock.copy(oppgaveId = "3", sykmeldingId = UUID.randomUUID())
        oppgaveRepository.lagreOppgave(oppgave)
        Mockito.`when`(gosysService.hentOppgave(oppgave.oppgaveId, oppgave.sykmeldingId.toString())).thenAnswer {
            oppgaveResponseMock
        }

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.hentPerson(oppgave.fnr, oppgave.sykmeldingId.toString())).thenAnswer {
            Person(
                fnr = "20086600138",
                navn = Navn("Fornavn", null, "Etternavn"),
                aktorId = "aktorid",
                bostedsadresse = null,
                oppholdsadresse = null,
                fodselsdato = LocalDate.of(1980, 5, 5),
            )
        }
        val avvistOppgave =
            digitaliseringsoppgaveService.avvisOppgave(
                oppgave.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunn,
                null,
            )
        val lagretOppgave = digitaliseringsoppgaveService.getDigitaiseringsoppgave(oppgave.oppgaveId)

        assertNotNull(lagretOppgave.oppgaveDbModel.ferdigstilt)
        assertEquals(avvistOppgave.oppgaveDbModel.ferdigstilt, lagretOppgave.oppgaveDbModel.ferdigstilt)
    }

    @Test
    fun testAvvisRollbackVedFeil() {
        oppgaveRepository.lagreOppgave(oppgaveMock)

        Mockito.`when`(gosysService.hentOppgave(oppgaveId, sykmeldingId.toString())).thenAnswer {
            oppgaveResponseMock
        }
        Mockito.`when`(
            gosysService.avvisOppgaveTilGosys(anyString(), anyString(), anyString(), anyString()),
        ).thenThrow(RuntimeException("Real bad error"))

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.hentPerson(oppgaveMock.fnr, oppgaveMock.sykmeldingId.toString())).thenAnswer {
            Person(
                fnr = "20086600138",
                navn = Navn("Fornavn", null, "Etternavn"),
                aktorId = "aktorid",
                bostedsadresse = null,
                oppholdsadresse = null,
                fodselsdato = LocalDate.of(1980, 5, 5),
            )
        }

        assertThrows<RuntimeException> {
            digitaliseringsoppgaveService.avvisOppgave(
                oppgaveMock.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunn,
                null,
            )
        }

        val oppgave = digitaliseringsoppgaveService.getDigitaiseringsoppgave(oppgaveMock.oppgaveId)

        assertNull(oppgave.oppgaveDbModel.ferdigstilt)
    }

    @Test
    fun testAvvisAnnet() {
        val oppgave = oppgaveMock.copy(oppgaveId = "2", sykmeldingId = UUID.randomUUID())
        oppgaveRepository.lagreOppgave(oppgave)
        Mockito.`when`(gosysService.hentOppgave(oppgave.oppgaveId, oppgave.sykmeldingId.toString())).thenAnswer {
            oppgaveResponseMock
        }

        val excpetedAvvisingsgrunnAnnet = Avvisingsgrunn.ANNET

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.hentPerson(oppgave.fnr, oppgave.sykmeldingId.toString())).thenAnswer {
            Person(
                fnr = "20086600138",
                navn = Navn("Fornavn", null, "Etternavn"),
                aktorId = "aktorid",
                bostedsadresse = null,
                oppholdsadresse = null,
                fodselsdato = LocalDate.of(1980, 5, 5),
            )
        }
        val avvistOppgave =
            digitaliseringsoppgaveService.avvisOppgave(
                oppgave.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunnAnnet,
                "Feil dato",
            )
        val lagretOppgave = digitaliseringsoppgaveService.getDigitaiseringsoppgave(oppgave.oppgaveId)

        assertNotNull(lagretOppgave.oppgaveDbModel.ferdigstilt)
        assertEquals(avvistOppgave.oppgaveDbModel.ferdigstilt, lagretOppgave.oppgaveDbModel.ferdigstilt)
    }

    @Test
    fun testAvvisAnnetRollbackVedFeil() {
        oppgaveRepository.lagreOppgave(oppgaveMock)

        val excpetedAvvisingsgrunnAnnet = Avvisingsgrunn.ANNET

        Mockito.`when`(gosysService.hentOppgave(oppgaveId, sykmeldingId.toString())).thenAnswer {
            oppgaveResponseMock
        }
        Mockito.`when`(
            gosysService.avvisOppgaveTilGosys(anyString(), anyString(), anyString(), anyString()),
        ).thenThrow(RuntimeException("Real bad error"))

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.hentPerson(oppgaveMock.fnr, oppgaveMock.sykmeldingId.toString())).thenAnswer {
            Person(
                fnr = "20086600138",
                navn = Navn("Fornavn", null, "Etternavn"),
                aktorId = "aktorid",
                bostedsadresse = null,
                oppholdsadresse = null,
                fodselsdato = LocalDate.of(1980, 5, 5),
            )
        }

        assertThrows<RuntimeException> {
            digitaliseringsoppgaveService.avvisOppgave(
                oppgaveMock.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunnAnnet,
                null,
            )
        }

        val oppgave = digitaliseringsoppgaveService.getDigitaiseringsoppgave(oppgaveMock.oppgaveId)

        assertNull(oppgave.oppgaveDbModel.ferdigstilt)
    }
}

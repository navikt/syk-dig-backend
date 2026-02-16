package no.nav.sykdig.utenlandsk.services

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.sykdig.IntegrationTest
import no.nav.sykdig.SykDigBackendApplication
import no.nav.sykdig.generated.types.Avvisingsgrunn
import no.nav.sykdig.gosys.GosysService
import no.nav.sykdig.gosys.models.GetOppgaveResponse
import no.nav.sykdig.gosys.models.OppgaveStatus
import no.nav.sykdig.gosys.models.OppgaveType
import no.nav.sykdig.pdl.Navn
import no.nav.sykdig.pdl.Person
import no.nav.sykdig.pdl.PersonService
import no.nav.sykdig.shared.metrics.MetricRegister
import no.nav.sykdig.utenlandsk.db.JournalpostSykmeldingRepository
import no.nav.sykdig.utenlandsk.models.OppgaveDbModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(classes = [SykDigBackendApplication::class])
class UtenlandskOppgaveServiceTest : IntegrationTest() {
    @Autowired lateinit var journalpostSykmeldingRepository: JournalpostSykmeldingRepository

    @MockitoBean lateinit var gosysService: GosysService

    @MockitoBean lateinit var personService: PersonService

    @MockitoBean lateinit var metricRegister: MetricRegister

    @Autowired lateinit var utenlandskOppgaveService: UtenlandskOppgaveService

    @MockitoBean lateinit var ferdigstillingService: FerdigstillingService

    final val sykmeldingId = UUID.randomUUID()

    final val oppgaveId = "1"

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
            status = OppgaveStatus.OPPRETTET,
            behandlesAvApplikasjon = "SMM",
            id = 1,
            tilordnetRessurs = "A123456",
            beskrivelse = "Dette var ikkje bra",
            oppgavetype = OppgaveType.BEH_SED.toString(),
            tildeltEnhetsnr = "0393",
            aktivDato = LocalDate.now(),
            duplikat = false,
            ferdigstiltTidspunkt = null,
            tema = null,
            metadata = null,
            journalpostId = null,
            prioritet = null,
            behandlingstema = null,
            behandlingstype = null,
        )

    val excpetedAvvisingsgrunn = Avvisingsgrunn.MANGLENDE_DIAGNOSE

    @Test
    fun testJournalpostSykmeldingAvvist() {
        journalpostSykmeldingRepository.insertJournalpostId("journalpostAvvist")
        val oppgave =
            oppgaveMock.copy(
                "journalpostAvvist-oppgave-avvist",
                sykmeldingId = UUID.randomUUID(),
                journalpostId = "journalpostAvvist",
            )
        Mockito.`when`(gosysService.hentOppgave(anyString(), anyString())).thenAnswer {
            oppgaveResponseMock
        }

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.getPerson(oppgave.fnr, oppgave.sykmeldingId.toString()))
            .thenAnswer {
                Person(
                    fnr = "20086600138",
                    navn = Navn("Fornavn", null, "Etternavn"),
                    aktorId = "aktorid",
                    bostedsadresse = null,
                    oppholdsadresse = null,
                    fodselsdato = LocalDate.of(1980, 5, 5),
                )
            }
        oppgaveRepository.lagreOppgave(oppgave)
        utenlandskOppgaveService.avvisOppgave(
            oppgave.oppgaveId,
            "Z123456",
            "Z123456@trygdeetaten.no",
            "0393",
            Avvisingsgrunn.MANGLENDE_DIAGNOSE,
            null,
        )
        val journalpostSykmelding =
            journalpostSykmeldingRepository.getJournalpostSykmelding("journalpostAvvist")
        assertNull(journalpostSykmelding)
    }

    @Test
    fun testJournalpostSykmeldingAvvist2() {
        val journalpostId = "journalpostAvvist2"
        val oppgaveId1 = "journalpostAvvist-oppgave-avvist-2"
        val oppgaveId2 = "journalpostAvvist-oppgave-avvist-3"
        journalpostSykmeldingRepository.insertJournalpostId(journalpostId)
        val oppgave =
            oppgaveMock.copy(
                oppgaveId1,
                sykmeldingId = UUID.randomUUID(),
                journalpostId = journalpostId,
            )
        Mockito.`when`(gosysService.hentOppgave(anyString(), anyString())).thenAnswer {
            oppgaveResponseMock
        }

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.getPerson(oppgave.fnr, oppgave.sykmeldingId.toString()))
            .thenAnswer {
                Person(
                    fnr = "20086600138",
                    navn = Navn("Fornavn", null, "Etternavn"),
                    aktorId = "aktorid",
                    bostedsadresse = null,
                    oppholdsadresse = null,
                    fodselsdato = LocalDate.of(1980, 5, 5),
                )
            }
        oppgaveRepository.lagreOppgave(oppgave)

        val oppgave2 =
            oppgaveMock.copy(
                oppgaveId2,
                sykmeldingId = UUID.randomUUID(),
                journalpostId = journalpostId,
            )
        oppgaveRepository.lagreOppgave(oppgave2)
        utenlandskOppgaveService.avvisOppgave(
            oppgave.oppgaveId,
            "Z123456",
            "Z123456@trygdeetaten.no",
            "0393",
            Avvisingsgrunn.MANGLENDE_DIAGNOSE,
            null,
        )
        val journalpostSykmelding =
            journalpostSykmeldingRepository.getJournalpostSykmelding(journalpostId)
        assertNotNull(journalpostSykmelding)
    }

    @Test
    fun testJournalpostSykmelding() {
        journalpostSykmeldingRepository.insertJournalpostId("journalpostAvvist")
        val oppgave =
            oppgaveMock.copy(
                "journalpostAvvist-oppgave-ok",
                sykmeldingId = UUID.randomUUID(),
                journalpostId = "journalpostAvvist",
            )
        oppgaveRepository.lagreOppgave(oppgave)
        val journalpostSykmelding =
            journalpostSykmeldingRepository.getJournalpostSykmelding("journalpostAvvist")
        assertNotNull(journalpostSykmelding)
    }

    @Test
    fun testAvvisOk() {
        val oppgave = oppgaveMock.copy(oppgaveId = "3", sykmeldingId = UUID.randomUUID())
        oppgaveRepository.lagreOppgave(oppgave)
        Mockito.`when`(gosysService.hentOppgave(oppgave.oppgaveId, oppgave.sykmeldingId.toString()))
            .thenAnswer { oppgaveResponseMock }

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.getPerson(oppgave.fnr, oppgave.sykmeldingId.toString()))
            .thenAnswer {
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
            utenlandskOppgaveService.avvisOppgave(
                oppgave.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunn,
                null,
            )
        val lagretOppgave = utenlandskOppgaveService.getDigitaiseringsoppgave(oppgave.oppgaveId)

        assertNotNull(lagretOppgave.oppgaveDbModel.ferdigstilt)
        assertEquals(
            avvistOppgave.oppgaveDbModel.ferdigstilt,
            lagretOppgave.oppgaveDbModel.ferdigstilt,
        )
    }

    @Test
    fun testAvvisRollbackVedFeil() {
        oppgaveRepository.lagreOppgave(oppgaveMock)

        Mockito.`when`(gosysService.hentOppgave(oppgaveId, sykmeldingId.toString())).thenAnswer {
            oppgaveResponseMock
        }
        Mockito.`when`(
                gosysService.avvisOppgaveTilGosys(
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                )
            )
            .thenThrow(RuntimeException("Real bad error"))

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(
                personService.getPerson(oppgaveMock.fnr, oppgaveMock.sykmeldingId.toString())
            )
            .thenAnswer {
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
            utenlandskOppgaveService.avvisOppgave(
                oppgaveMock.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunn,
                null,
            )
        }

        val oppgave = utenlandskOppgaveService.getDigitaiseringsoppgave(oppgaveMock.oppgaveId)

        assertNull(oppgave.oppgaveDbModel.ferdigstilt)
    }

    @Test
    fun testAvvisAnnet() {
        val oppgave = oppgaveMock.copy(oppgaveId = "2", sykmeldingId = UUID.randomUUID())
        oppgaveRepository.lagreOppgave(oppgave)
        Mockito.`when`(gosysService.hentOppgave(oppgave.oppgaveId, oppgave.sykmeldingId.toString()))
            .thenAnswer { oppgaveResponseMock }

        val excpetedAvvisingsgrunnAnnet = Avvisingsgrunn.ANNET

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(personService.getPerson(oppgave.fnr, oppgave.sykmeldingId.toString()))
            .thenAnswer {
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
            utenlandskOppgaveService.avvisOppgave(
                oppgave.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunnAnnet,
                "Feil dato",
            )
        val lagretOppgave = utenlandskOppgaveService.getDigitaiseringsoppgave(oppgave.oppgaveId)

        assertNotNull(lagretOppgave.oppgaveDbModel.ferdigstilt)
        assertEquals(
            avvistOppgave.oppgaveDbModel.ferdigstilt,
            lagretOppgave.oppgaveDbModel.ferdigstilt,
        )
    }

    @Test
    fun testAvvisAnnetRollbackVedFeil() {
        oppgaveRepository.lagreOppgave(oppgaveMock)

        val excpetedAvvisingsgrunnAnnet = Avvisingsgrunn.ANNET

        Mockito.`when`(gosysService.hentOppgave(oppgaveId, sykmeldingId.toString())).thenAnswer {
            oppgaveResponseMock
        }
        Mockito.`when`(
                gosysService.avvisOppgaveTilGosys(
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                    anyString(),
                )
            )
            .thenThrow(RuntimeException("Real bad error"))

        Mockito.`when`(metricRegister.avvistSendtTilGosys).thenAnswer {
            SimpleMeterRegistry().counter("AVVIST_SENDT_TIL_GOSYS")
        }
        Mockito.`when`(
                personService.getPerson(oppgaveMock.fnr, oppgaveMock.sykmeldingId.toString())
            )
            .thenAnswer {
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
            utenlandskOppgaveService.avvisOppgave(
                oppgaveMock.oppgaveId,
                "Z123456",
                "Z123456@trygdeetaten.no",
                "0393",
                excpetedAvvisingsgrunnAnnet,
                null,
            )
        }

        val oppgave = utenlandskOppgaveService.getDigitaiseringsoppgave(oppgaveMock.oppgaveId)

        assertNull(oppgave.oppgaveDbModel.ferdigstilt)
    }
}

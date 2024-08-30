package no.nav.syfo.oppgave

import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.oppgave.client.OppgaveResponse
import no.nav.syfo.oppgave.saf.SafJournalpostService
import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import no.nav.syfo.oppgave.sykdig.SykDigProducer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OppgaveServiceTest {
    private val oppgaveClient = mockk<OppgaveClient>()
    private val safJournalpostService = mockk<SafJournalpostService>()
    private val sykDigProducer = mockk<SykDigProducer>()

    private val oppgaveService =
        OppgaveService(oppgaveClient, safJournalpostService, sykDigProducer, "prod-gcp")

    @BeforeEach
    fun setup() {
        clearMocks(oppgaveClient, safJournalpostService, sykDigProducer)
        coEvery { safJournalpostService.getDokumenter(any(), any(), any()) } returns
            listOf(
                DokumentMedTittel("123", "123dokument"),
                DokumentMedTittel("456", "456dokument"),
            )
        coEvery { sykDigProducer.send(any(), any()) } just Runs
    }

    @Test
    internal fun `OppgaveService henter dokumentInfoId for utenlandsk sykmelding-oppgave som kommer fra Rina`() {
        coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
            OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = null,
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = null,
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND,
            )
        coEvery { oppgaveClient.oppdaterOppgave(any(), any()) } just Runs

        runBlocking {
            oppgaveService.handleOppgave(1L, "fnr")
        }
        coVerify { safJournalpostService.getDokumenter("5566", any(), any()) }
        coVerify {
            oppgaveClient.oppdaterOppgave(
                match { it.id == 1 && it.versjon == 1 && it.behandlesAvApplikasjon == "SMD" },
                any(),
            )
        }
        coVerify {
            sykDigProducer.send(
                any(),
                match {
                    it.oppgaveId == "1" &&
                        it.journalpostId == "5566" &&
                        it.fnr == "fnr" &&
                        it.dokumentInfoId == "123" &&
                        it.type == "UTLAND" &&
                        it.dokumenter ==
                        listOf(
                            DokumentMedTittel("123", "123dokument"),
                            DokumentMedTittel("456", "456dokument"),
                        )
                },
            )
        }
    }

    @Test
    internal fun `OppgaveService genter ikke dokumentInfoId for utenlandsk sykmelding-oppgave med behandlesAvApplikasjon SMD`() {
        coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
            OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = "SMD",
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = null,
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND,
            )

        runBlocking {
            oppgaveService.handleOppgave(1L, "fnr")
        }
        coVerify(exactly = 0) { safJournalpostService.getDokumenter(any(), any(), any()) }
    }

    @Test
    internal fun `Henter ikke dokumentInfoId for ferdigstilt utenlandsk sykmelding-oppgave som kommer fra Rina`() {
        coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
            OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = null,
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = "2023-01-18T09:55:15.729+01:00",
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND,
            )
        runBlocking {
            oppgaveService.handleOppgave(1L, "fnr")
        }
        coVerify(exactly = 0) { safJournalpostService.getDokumenter(any(), any(), any()) }
    }

    @Test
    internal fun `Sender sykmelding til syk-dig i prod-gcp`() {
        val oppgavesServiceProd =
            OppgaveService(oppgaveClient, safJournalpostService, sykDigProducer, "prod-gcp")
        coEvery { oppgaveClient.oppdaterOppgave(any(), any()) } just Runs
        coEvery { oppgaveClient.hentOppgave(any(), any()) } returns
            OppgaveResponse(
                journalpostId = "5566",
                behandlesAvApplikasjon = null,
                tema = "SYM",
                behandlingstema = null,
                oppgavetype = "JFR",
                behandlingstype = "ae0106",
                versjon = 1,
                metadata = mapOf("RINA_SAKID" to "111"),
                ferdigstiltTidspunkt = null,
                tildeltEnhetsnr = NAV_OPPFOLGNING_UTLAND,
            )
        runBlocking {
            oppgavesServiceProd.handleOppgave(1L, "fnr")
        }
        coVerify { safJournalpostService.getDokumenter("5566", any(), any()) }
        coVerify(exactly = 1) { oppgaveClient.oppdaterOppgave(any(), any()) }
        coVerify(exactly = 1) { sykDigProducer.send(any(), any()) }
    }
}

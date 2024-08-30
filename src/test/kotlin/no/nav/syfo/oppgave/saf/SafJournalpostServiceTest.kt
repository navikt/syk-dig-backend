package no.nav.syfo.oppgave.saf

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.accesstoken.AccessTokenClient
import no.nav.syfo.oppgave.saf.client.SafGraphQlClient
import no.nav.syfo.oppgave.saf.client.model.DokumentInfo
import no.nav.syfo.oppgave.saf.client.model.Dokumentvariant
import no.nav.syfo.oppgave.saf.client.model.FindJournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.JournalpostResponse
import no.nav.syfo.oppgave.saf.client.model.ResponseData
import no.nav.syfo.oppgave.saf.model.DokumentMedTittel
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SafJournalpostServiceTest {
    private val safGraphQlClient = mockk<SafGraphQlClient>()
    private val accessTokenClient = mockk<AccessTokenClient>()

    private val safJournalpostService =
        SafJournalpostService(safGraphQlClient, accessTokenClient, "scope")

    @BeforeEach
    fun setup() {
        clearMocks(safGraphQlClient, accessTokenClient)
        coEvery { accessTokenClient.getAccessToken(any()) } returns "token"
    }

    @Test
    internal fun `SafJournalpostService finner dokumentInfoId for PDF`() {
        coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns
            FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    "123",
                                    "tittel",
                                    "S055",
                                    listOf(Dokumentvariant("ARKIV")),
                                ),
                                DokumentInfo(
                                    "456",
                                    "tittel",
                                    null,
                                    listOf(Dokumentvariant("ORIGINAL")),
                                ),
                            ),
                        journalstatus = "MOTTATT",
                        kanal = "ESSI",
                    ),
                ),
                emptyList(),
            )

        val dokumentInfoId = safJournalpostService.getDokumenter("jpId", "sporing", "rina")

        dokumentInfoId shouldBeEqualTo listOf(DokumentMedTittel("123", "tittel"))
    }

    @Test
    internal fun `SafJournalpostService returnerer null hvis journalposten er journalført allerede`() {
        coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns
            FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    "123",
                                    "tittel",
                                    "S055",
                                    listOf(Dokumentvariant("ARKIV")),
                                ),
                                DokumentInfo(
                                    "456",
                                    "tittel",
                                    null,
                                    listOf(Dokumentvariant("ORIGINAL")),
                                ),
                            ),
                        journalstatus = "FERDIGSTILT",
                        kanal = "ESSI",
                    ),
                ),
                emptyList(),
            )
        val dokumentInfoId = safJournalpostService.getDokumenter("jpId", "sporing", "rina")
        dokumentInfoId shouldBeEqualTo null
    }

    @Test
    internal fun `SafJournalpostService kaster feil hvis dokumentliste ikke inneholder PDF`() {
        coEvery { safGraphQlClient.findJournalpost(any(), any(), any()) } returns
            FindJournalpostResponse(
                ResponseData(
                    JournalpostResponse(
                        dokumenter =
                            listOf(
                                DokumentInfo(
                                    "123",
                                    "tittel",
                                    "S055",
                                    listOf(Dokumentvariant("SLADDET")),
                                ),
                                DokumentInfo(
                                    "456",
                                    "tittel",
                                    null,
                                    listOf(Dokumentvariant("ORIGINAL")),
                                ),
                            ),
                        journalstatus = "MOTTATT",
                        kanal = "ESSI",
                    ),
                ),
                emptyList(),
            )

        assertFailsWith<RuntimeException> {
            safJournalpostService.getDokumenter("jpId", "sporing", "rina")
        }
    }
}

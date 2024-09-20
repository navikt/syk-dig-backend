package no.nav.sykdig.saf

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.SafJournalpostService
import no.nav.sykdig.digitalisering.saf.graphql.DokumentInfo
import no.nav.sykdig.digitalisering.saf.graphql.Dokumentvariant
import no.nav.sykdig.digitalisering.saf.graphql.Journalstatus
import no.nav.sykdig.digitalisering.saf.graphql.SafJournalpost
import no.nav.sykdig.digitalisering.saf.graphql.SafQueryJournalpost
import org.amshove.kluent.internal.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SafJournalpostServiceTest {
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient = mockk()
    private val safJournalpostService = SafJournalpostService(safJournalpostGraphQlClient)

    @Test
    fun `SafJournalpostService finner dokumentInfoId for PDF`() {
        val journalpostId = "123"
        val sykmeldingId = "syk-456"
        val source = "rina"

        coEvery { safJournalpostGraphQlClient.getJournalpost(journalpostId) } returns
            SafQueryJournalpost(
                SafJournalpost(
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "dok1",
                                tittel = "Dokument 1",
                                dokumentvarianter = listOf(Dokumentvariant(variantformat = "ARKIV")),
                                brevkode = "",
                            ),
                        ),
                    kanal = "EESSI",
                    avsenderMottaker = null,
                    bruker = null,
                    tema = null,
                ),
            )

        val result = safJournalpostService.getDokumenterM2m(journalpostId, sykmeldingId, source)

        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("dok1", result[0].dokumentInfoId)
        assertEquals("Dokument 1", result[0].tittel)
    }

    @Test
    fun `SafJournalpostService returnerer null hvis journalposten er journalført allerede`() {
        val journalpostId = "123"
        val sykmeldingId = "syk-456"
        val source = "rina"

        coEvery { safJournalpostGraphQlClient.getJournalpost(journalpostId) } returns
            SafQueryJournalpost(
                SafJournalpost(
                    journalstatus = Journalstatus.JOURNALFOERT,
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "dok1",
                                tittel = "Dokument 1",
                                dokumentvarianter = listOf(Dokumentvariant(variantformat = "ARKIV")),
                                brevkode = "",
                            ),
                        ),
                    kanal = "EESSI",
                    avsenderMottaker = null,
                    bruker = null,
                    tema = null,
                ),
            )
        val result = safJournalpostService.getDokumenterM2m(journalpostId, sykmeldingId, source)
        assertNull(result)
    }

    @Test
    fun `SafJournalpostService kaster feil hvis dokumentliste ikke inneholder PDF`() {
        val journalpostId = "123"
        val sykmeldingId = "syk-456"
        val source = "rina"

        coEvery { safJournalpostGraphQlClient.getJournalpost(journalpostId) } returns
            SafQueryJournalpost(
                SafJournalpost(
                    journalstatus = Journalstatus.MOTTATT,
                    dokumenter =
                        listOf(
                            DokumentInfo(
                                dokumentInfoId = "dok1",
                                tittel = "Dokument 1",
                                dokumentvarianter = listOf(Dokumentvariant(variantformat = "NON-ARKIV")),
                                brevkode = "1",
                            ),
                        ),
                    kanal = "EESSI",
                    avsenderMottaker = null,
                    bruker = null,
                    tema = null,
                ),
            )

        val exception =
            assertFailsWith<RuntimeException> {
                safJournalpostService.getDokumenterM2m(journalpostId, sykmeldingId, source)
            }
        assertEquals("Journalpost mangler PDF, $sykmeldingId", exception.message)
    }
}

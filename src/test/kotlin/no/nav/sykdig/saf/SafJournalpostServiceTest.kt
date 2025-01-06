package no.nav.sykdig.saf

import io.mockk.every
import io.mockk.mockk
import no.nav.sykdig.digitalisering.exceptions.MissingJournalpostException
import no.nav.sykdig.digitalisering.saf.SafJournalpostGraphQlClient
import no.nav.sykdig.digitalisering.saf.SafJournalpostService
import no.nav.sykdig.digitalisering.saf.graphql.*
import org.amshove.kluent.assertionError
import org.amshove.kluent.internal.assertFailsWith
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SafJournalpostServiceTest {
    private val safJournalpostGraphQlClient: SafJournalpostGraphQlClient = mockk()
    private val safJournalpostService = SafJournalpostService(safJournalpostGraphQlClient)

    @Test
    fun `SafJournalpostService finner dokumentInfoId for PDF`() {
        val journalpostId = "123"
        val sykmeldingId = "syk-456"
        val source = "rina"

        every { safJournalpostGraphQlClient.getJournalpostM2m(eq(journalpostId)) } returns
            SafQueryJournalpost(
                SafJournalpost(
                    tittel = "tittel",
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

        every { safJournalpostGraphQlClient.getJournalpostM2m(journalpostId) } returns
            SafQueryJournalpost(
                SafJournalpost(
                    tittel = "tittel",
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

        every { safJournalpostGraphQlClient.getJournalpostM2m(journalpostId) } returns
            SafQueryJournalpost(
                SafJournalpost(
                    tittel = "tittel",
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

    @Test
    fun `er ikke journalført fordi status er mottatt`() {
        val journalpostId = "123"
        every { safJournalpostGraphQlClient.getJournalpostNasjonal(journalpostId) } returns SafQueryJournalpostNasjonal(
            journalpost = SafJournalpostNasjonal(
                journalstatus = Journalstatus.MOTTATT,
            )
        )
        val erIkkeJournalfort = safJournalpostService.erIkkeJournalfort(journalpostId)
        assertTrue(erIkkeJournalfort)
    }
    @Test
    fun `er ikke journalført fordi safjournalpost er null`() {
        val journalpostId = "123"
        every { safJournalpostGraphQlClient.getJournalpostNasjonal(journalpostId) } returns SafQueryJournalpostNasjonal(
            journalpost = null
        )
        val erIkkeJournalfort = safJournalpostService.erIkkeJournalfort(journalpostId)
        assertFalse(erIkkeJournalfort)
    }
    @Test
    fun `er journalført fordi status er ukjent`() {
        val journalpostId = "123"
        every { safJournalpostGraphQlClient.getJournalpostNasjonal(journalpostId) } returns
                SafQueryJournalpostNasjonal(
                    journalpost = SafJournalpostNasjonal(
                        Journalstatus.UKJENT
                    )
                )

        val erIkkeJournalfort = safJournalpostService.erIkkeJournalfort(journalpostId)
        assertFalse(erIkkeJournalfort)
    }
}

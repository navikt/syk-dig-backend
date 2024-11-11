package no.nav.sykdig.digitalisering.helsenett

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.sykdig.digitalisering.exceptions.SykmelderNotFoundException
import no.nav.sykdig.digitalisering.helsenett.client.HelsenettClient
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Godkjenning
import no.nav.sykdig.digitalisering.papirsykmelding.api.model.Kode
import no.nav.sykdig.digitalisering.pdl.Navn
import no.nav.sykdig.digitalisering.pdl.Person
import no.nav.sykdig.digitalisering.pdl.PersonService
import org.amshove.kluent.internal.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SykmelderServiceTest {
    private val pdlService = mockk<PersonService>()
    private val helsenettClient = mockk<HelsenettClient>()
    private val sykmelderService = SykmelderService(helsenettClient, pdlService)

    @Test
    fun `get sykmelder happy case`() {
        val hprNummer = "123456789"
        val fnr = "12345678910"
        val fornavn = "Ola"
        val mellomnavn = "Mellomnavn"
        val etternavn = "Normann"

        val expectedPerson = Person(
            fnr = fnr,
            navn = Navn(fornavn, mellomnavn, etternavn),
            aktorId = fnr,
            bostedsadresse = null,
            oppholdsadresse = null,
            fodselsdato = null
        )

        val expectedBehandler = Behandler(
            godkjenninger = listOf(
                Godkjenning(Kode(true, 1, null), Kode(true, 1, null))
            ),
            fnr = fnr,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn
        )

        coEvery { pdlService.getPerson(any(), any()) } returns expectedPerson
        coEvery { helsenettClient.getBehandler(hprNummer, "callid") } returns expectedBehandler

        val sykmelder = runBlocking { sykmelderService.getSykmelder(hprNummer, "callid") }

        assertEquals(hprNummer, sykmelder.hprNummer)
        assertEquals(fnr, sykmelder.fnr)
        assertEquals(fornavn, sykmelder.fornavn)
        assertEquals(mellomnavn, sykmelder.mellomnavn)
        assertEquals(etternavn, sykmelder.etternavn)

        coVerify { pdlService.getPerson(any(), any()) }
        coVerify { helsenettClient.getBehandler(hprNummer, "callid") }
    }

    @Test
    fun `get sykmelder does not exists in hpr`() {
        val hprNummer = "123456789"
        val fnr = "1234567"
        val fornavn = "Ola"
        val mellomnavn = "Mellomnavn"
        val etternavn = "Normann"

        val expectedPerson = Person(
            fnr = fnr,
            navn = Navn(fornavn, mellomnavn, etternavn),
            aktorId = fnr,
            bostedsadresse = null,
            oppholdsadresse = null,
            fodselsdato = null
        )

        coEvery { pdlService.getPerson(any(), any()) } returns expectedPerson
        coEvery { helsenettClient.getBehandler(hprNummer, "callid") } throws SykmelderNotFoundException("Kunne ikke hente fnr for hpr $hprNummer")

        val exception = runBlocking { assertFailsWith<SykmelderNotFoundException> { sykmelderService.getSykmelder(hprNummer, "callid") } }
        assertEquals("Kunne ikke hente fnr for hpr $hprNummer", exception.message)
    }
}

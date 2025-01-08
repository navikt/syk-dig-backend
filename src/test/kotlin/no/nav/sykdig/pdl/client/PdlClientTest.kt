package no.nav.sykdig.pdl.client

import no.nav.sykdig.pdl.client.graphql.mapToPdlResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class PdlClientTest {
    @Test
    fun shouldmapToPdlResponse() {
        val json = String(Files.readAllBytes(Paths.get("src/test/resources/pdl.json")), StandardCharsets.UTF_8)

        val pdlResponse = mapToPdlResponse(json)

        assertEquals("UKONTROVERSIELL", pdlResponse.hentPerson?.navn?.first()?.fornavn)
    }
}

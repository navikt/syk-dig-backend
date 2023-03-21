package no.nav.sykdig.pdl.client

import no.nav.sykdig.digitalisering.pdl.client.graphql.Data
import no.nav.sykdig.objectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class PdlClientTest {

    @Test
    fun mapToPdlResponse() {
        val pdlResponse = objectMapper.readValue(
            PdlClientTest::class.java.getResourceAsStream("/pdl.json"),
            Data::class.java,
        ).data

        Assertions.assertEquals("UKONTROVERSIELL", pdlResponse?.hentPerson?.navn?.first()?.fornavn)
    }
}

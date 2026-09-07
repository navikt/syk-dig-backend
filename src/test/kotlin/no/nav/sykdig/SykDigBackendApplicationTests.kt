package no.nav.sykdig

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SykDigBackendApplicationTests : IntegrationTest() {
    @Autowired lateinit var flyway: Flyway

    @Test fun contextLoads() {}

    @Test
    fun `database migrations run on startup`() {
        assertNotNull(flyway.info().current())
        assertTrue(flyway.info().pending().isEmpty())
    }
}

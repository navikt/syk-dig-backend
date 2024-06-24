package no.nav.sykdig

import no.nav.sykdig.db.OppgaveRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureObservability
@SpringBootTest(classes = [SykDigBackendApplication::class])
abstract class FellesTestOppsett {
    @Autowired
    lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    lateinit var oppgaveRepository: OppgaveRepository

    @AfterAll
    fun opprydning() {
        namedParameterJdbcTemplate.update("DELETE FROM sykmelding", MapSqlParameterSource())
        namedParameterJdbcTemplate.update("DELETE FROM journalpost_sykmelding", MapSqlParameterSource())
    }

    companion object {
        init {

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }

            PostgreSQLContainer14().apply {
                withCommand("postgres", "-c", "wal_level=logical")
                start()
                System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
                System.setProperty("spring.datasource.username", username)
                System.setProperty("spring.datasource.password", password)
            }
        }
    }
}

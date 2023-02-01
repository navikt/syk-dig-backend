package no.nav.sykdig

import no.nav.sykdig.db.OppgaveRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.concurrent.thread

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMetrics
@SpringBootTest(classes = [SykDigBackendApplication::class])
abstract class FellesTestOppsett {

    @Autowired
    lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @Autowired
    lateinit var oppgaveRepository: OppgaveRepository

    companion object {

        init {
            val threads = mutableListOf<Thread>()

            thread {
                KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1")).apply {
                    start()
                    System.setProperty("KAFKA_BROKERS", bootstrapServers)
                }
            }.also { threads.add(it) }

            thread {
                PostgreSQLContainer14().apply {
                    withCommand("postgres", "-c", "wal_level=logical")
                    start()
                    System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
                    System.setProperty("spring.datasource.username", username)
                    System.setProperty("spring.datasource.password", password)
                }
            }.also { threads.add(it) }

            threads.forEach { it.join() }
        }
    }

    @AfterAll
    fun opprydning() {
        namedParameterJdbcTemplate.update("DELETE FROM sykmelding", MapSqlParameterSource())
    }
}

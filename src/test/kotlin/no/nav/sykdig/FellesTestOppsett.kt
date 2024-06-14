package no.nav.sykdig

import no.nav.sykdig.db.OppgaveRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

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
        private val logger = LoggerFactory.getLogger(FellesTestOppsett::class.java)

        @Container
        val postgresql: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:14-alpine").apply {
                withCommand("postgres", "-c", "wal_level=logical")
            }

        @Container
        val kafkaContainer: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1"))

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            postgresql.start()
            kafkaContainer.start()

            registry.add("spring.datasource.url") { "${postgresql.jdbcUrl}&reWriteBatchedInserts=true" }
            registry.add("spring.datasource.username", postgresql::getUsername)
            registry.add("spring.datasource.password", postgresql::getPassword)
            registry.add("KAFKA_BROKERS", kafkaContainer::getBootstrapServers)
        }
    }
}

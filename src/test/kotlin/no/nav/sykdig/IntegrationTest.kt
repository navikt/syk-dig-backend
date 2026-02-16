package no.nav.sykdig

import no.nav.sykdig.nasjonal.db.NasjonalOppgaveRepository
import no.nav.sykdig.nasjonal.db.NasjonalSykmeldingRepository
import no.nav.sykdig.utenlandsk.db.OppgaveRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(classes = [SykDigBackendApplication::class])
abstract class IntegrationTest {
    @Autowired lateinit var namedParameterJdbcTemplate: NamedParameterJdbcTemplate

    @Autowired lateinit var nasjonalOppgaveRepository: NasjonalOppgaveRepository

    @Autowired lateinit var nasjonalSykmeldingRepository: NasjonalSykmeldingRepository

    @Autowired lateinit var oppgaveRepository: OppgaveRepository

    companion object {

        val postgres =
            PostgreSQLContainer("postgres:14-alpine")
                .withUsername("postgres")
                .withPassword("postgres")
                .withDatabaseName("postgres")
                .withUrlParam("reWriteBatchedInserts", "true")
                .withCommand("postgres", "-c", "wal_level=logical")
                .apply { start() }

        val kafka =
            ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.1.0")).apply {
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            registry.add("KAFKA_BROKERS", kafka::getBootstrapServers)
        }
    }
}

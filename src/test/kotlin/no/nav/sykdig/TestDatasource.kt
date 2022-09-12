package no.nav.sykdig

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource
import kotlin.concurrent.thread

private class PostgreSQLContainer2 : PostgreSQLContainer<PostgreSQLContainer2>("postgres:14-alpine")

@Configuration
class TestDatasource {

    private val psqlContainer: PostgreSQLContainer2
    init {
        val threads = mutableListOf<Thread>()

        thread {
            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.0.1")).apply {
                start()
                System.setProperty("KAFKA_BROKERS", bootstrapServers)
            }
        }.also { threads.add(it) }


        psqlContainer = PostgreSQLContainer2()
        psqlContainer.start()



        threads.forEach { it.join() }
    }
    @Bean
    fun getDataSource(): DataSource? {
        val tempDatasource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = psqlContainer.jdbcUrl
                username = psqlContainer.username
                password = psqlContainer.password
                maximumPoolSize = 10
                minimumIdle = 3
                idleTimeout = 10000
                maxLifetime = 300000
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
                validate()
            }
        )
        return tempDatasource
    }
}
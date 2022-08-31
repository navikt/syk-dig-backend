package no.nav.sykdig

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.concurrent.thread

@SpringBootTest
class SykDigBackendApplicationTests {

	private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

	companion object {

		init {
			val threads = mutableListOf<Thread>()

			thread {
				PostgreSQLContainer14().apply {
					start()
					System.setProperty("spring.datasource.url", "$jdbcUrl&reWriteBatchedInserts=true")
					System.setProperty("spring.datasource.username", username)
					System.setProperty("spring.datasource.password", password)
				}
			}.also { threads.add(it) }

			threads.forEach { it.join() }
		}
	}

	@Test
	fun contextLoads() {
	}

}

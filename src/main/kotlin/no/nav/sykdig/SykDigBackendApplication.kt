package no.nav.sykdig

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableKafka
class SykDigBackendApplication

fun main(args: Array<String>) {
    runApplication<SykDigBackendApplication>(*args)
}

data class ApplicationState(
    var alive: Boolean = true,
    var ready: Boolean = true,
)

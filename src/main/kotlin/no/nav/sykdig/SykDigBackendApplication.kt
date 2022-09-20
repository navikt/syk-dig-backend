package no.nav.sykdig

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class SykDigBackendApplication

fun main(args: Array<String>) {
    runApplication<SykDigBackendApplication>(*args)
}

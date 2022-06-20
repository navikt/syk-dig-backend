package no.nav.sykdig

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SykDigBackendApplication

fun main(args: Array<String>) {
	runApplication<SykDigBackendApplication>(*args)
}

package no.nav.sykdig

import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableJwtTokenValidation
class SykDigBackendApplication

fun main(args: Array<String>) {
	runApplication<SykDigBackendApplication>(*args)
}

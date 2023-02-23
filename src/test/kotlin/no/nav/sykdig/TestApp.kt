package no.nav.sykdig

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability

@SpringBootApplication
@AutoConfigureObservability
class TestApp

fun main(args: Array<String>) {
    runApplication<TestApp>(*args)
}

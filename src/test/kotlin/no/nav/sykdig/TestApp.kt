package no.nav.sykdig

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics

@SpringBootApplication
@AutoConfigureMetrics
class TestApp

fun main(args: Array<String>) {
    runApplication<TestApp>(*args)
}

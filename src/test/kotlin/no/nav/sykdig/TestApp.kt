package no.nav.sykdig

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics
import org.springframework.boot.runApplication

@SpringBootApplication @AutoConfigureMetrics class TestApp

fun main(args: Array<String>) {
    runApplication<TestApp>(*args)
}

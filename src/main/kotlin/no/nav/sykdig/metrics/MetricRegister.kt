package no.nav.sykdig.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

const val METRICS_NS = "sykdigbackend"

@Component
class MetricRegister(registry: MeterRegistry) {
    val reg = registry

    val MOTTATT_OPPGAVE = registry.counter(
        "${METRICS_NS}_mottatt_oppgave_counter"
    )

    val FERDIGSTILT_OPPGAVE = registry.counter(
        "${METRICS_NS}_ferdigstilt_oppgave_counter"
    )

    val SENDT_TIL_GOSYS = registry.counter(
        "${METRICS_NS}_sendt_til_gosys_counter"
    )
}

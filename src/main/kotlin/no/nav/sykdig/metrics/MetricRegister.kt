package no.nav.sykdig.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

const val METRICS_NS = "sykdigbackend"

@Component
class MetricRegister(registry: MeterRegistry) {
    val reg = registry

    val mottatOppgave =
        registry.counter(
            "${METRICS_NS}_mottatt_oppgave_counter",
        )

    val ferdigstiltOppgave =
        registry.counter(
            "${METRICS_NS}_ferdigstilt_oppgave_counter",
        )

    val sendtTilGosys =
        registry.counter(
            "${METRICS_NS}_sendt_til_gosys_counter",
        )

    val avvistSendtTilGosys =
        registry.counter(
            "${METRICS_NS}_avvist_sendt_til_gosys_counter",
        )
}

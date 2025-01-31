package no.nav.sykdig.shared.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.stereotype.Component

const val METRICS_NS = "sykdigbackend"

@Component
class MetricRegister(private val registry: MeterRegistry) {
    val reg = registry

    val mottatOppgave =
        registry.counter(
            "${METRICS_NS}_mottatt_oppgave_counter",
        )

    val ferdigstiltOppgave =
        registry.counter(
            "${METRICS_NS}_ferdigstilt_oppgave_counter",
        )

    val oppdatertSykmeldingCounter = registry.counter("${METRICS_NS}_oppdatert_utenlandsk_sykmelding_counter")

    val sendtTilGosys =
        registry.counter(
            "${METRICS_NS}_sendt_til_gosys_counter",
        )

    val sendtTilGosysNasjonal =
        registry.counter(
            "${METRICS_NS}_sendt_til_gosys_nasjonal_counter",
        )

    val avvistSendtTilGosys =
        registry.counter(
            "${METRICS_NS}_avvist_sendt_til_gosys_counter",
        )

    val incoming_message_counter =
        registry.counter(
            "${METRICS_NS}_incoming_message_count",
        )

    val opprett_nasjonal_oppgave_counter =
        registry.counter(
            "${METRICS_NS}_opprett_oppgave_counter",
        )

    val message_stored_in_db_counter =
        registry.counter(
            "${METRICS_NS}_message_stored_in_db_counter",
        )

    fun incrementNewSykmelding(
        type: String,
        kanal: String?,
    ) {
        registry.counter(
            "${METRICS_NS}_create_sykmelding_counter",
            Tags.of("type", type, "kanal", kanal),
        ).increment()
    }
}

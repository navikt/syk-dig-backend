package no.nav.oppgavelytter.nais.prometheus

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.io.StringWriter

@RestController
class PrometheusController(
    private val collectorRegistry: CollectorRegistry,
) {
    @GetMapping("/internal/prometheus", produces = [TextFormat.CONTENT_TYPE_004])
    @ResponseBody
    fun prometheusMetrics(
        @RequestParam(name = "name[]", required = false) names: List<String>?,
    ): String {
        val metricsNames = names?.toSet() ?: emptySet()
        val writer = StringWriter()

        TextFormat.write004(writer, collectorRegistry.filteredMetricFamilySamples(metricsNames))
        return writer.toString()
    }
}

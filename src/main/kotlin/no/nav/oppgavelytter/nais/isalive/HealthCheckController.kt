package no.nav.oppgavelytter.nais.isalive
import no.nav.sykdig.ApplicationState
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthCheckController(
    private val applicationState: ApplicationState,
) {
    @GetMapping("/internal/is_alive")
    fun isAlive(): String {
        return if (applicationState.alive) {
            "I'm alive! :)"
        } else {
            throw IllegalStateException("I'm dead x_x")
        }
    }
}

package no.nav.syfo.nais.isready

import no.nav.sykdig.ApplicationState
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ReadyController(
    private val applicationState: ApplicationState,
) {
    @GetMapping("/internal/is_ready")
    fun isAlive(): String {
        return if (applicationState.ready) {
            "I'm ready! :)"
        } else {
            throw IllegalStateException("Please wait! I'm not ready")
        }
    }
}

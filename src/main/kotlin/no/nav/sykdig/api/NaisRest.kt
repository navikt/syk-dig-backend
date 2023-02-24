package no.nav.sykdig.api

import no.nav.security.token.support.core.api.Unprotected
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class NaisRest {
    @Unprotected
    @GetMapping("/internal/is_alive")
    fun isAlive(): String {
        return "alive"
    }

    @Unprotected
    @GetMapping("/internal/is_ready")
    fun isReady(): String {
        return "ready"
    }
}

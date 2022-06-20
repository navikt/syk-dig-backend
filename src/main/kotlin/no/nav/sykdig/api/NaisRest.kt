package no.nav.sykdig.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class NaisRest {
    @GetMapping("/internal2/is_alive")
    fun isAlive() : String {
        return "alive"
    }

    @GetMapping("/internal2/is_ready")
    fun isReady() : String {
        return "ready"
    }
}
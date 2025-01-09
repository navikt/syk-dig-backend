package no.nav.sykdig.utenlandsk.poststed

import no.nav.sykdig.shared.applog
import no.nav.sykdig.shared.objectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.net.InetAddress

@Component
class LeaderElection(
    private val plainTextUtf8WebClient: WebClient,
    @Value("\${elector.path}") private val electorPath: String,
) {
    val log = applog()

    fun isLeader(): Boolean {
        if (electorPath == "dont_look_for_leader") {
            log.info("Ser ikke etter leader, returnerer at jeg er leader")
            return true
        }
        return isPodLeader()
    }

    private fun isPodLeader(): Boolean {
        val hostname: String = InetAddress.getLocalHost().hostName

        val uriString = UriComponentsBuilder.fromHttpUrl(getHttpPath(electorPath)).toUriString()

        try {
            val result = plainTextUtf8WebClient
                .get()
                .uri(uriString)
                .retrieve()
                .onStatus({ status -> status != HttpStatus.OK }) { response ->
                    response.bodyToMono(String::class.java).flatMap {
                        Mono.error(RuntimeException("Kall mot elector feiler med HTTP-${response.statusCode()}"))
                    }
                }
                .bodyToMono(String::class.java)
                .block() // Block to get the response synchronously

            // Parsing response if successful
            result?.let {
                val leader: Leader = objectMapper.readValue(it, Leader::class.java)
                return leader.name == hostname
            }

            val message = "Kall mot elector returnerer ikke data"
            log.error(message)
            throw RuntimeException(message)
        } catch (e: Exception) {
            log.error("Feil ved kall mot elector: ${e.message}", e)
            throw e
        }
    }

    private fun getHttpPath(url: String): String =
        when (url.startsWith("http://")) {
            true -> url
            else -> "http://$url"
        }

    private data class Leader(val name: String)
}

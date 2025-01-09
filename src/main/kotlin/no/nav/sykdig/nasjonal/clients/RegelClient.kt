package no.nav.sykdig.nasjonal.clients

import kotlinx.coroutines.reactor.awaitSingle
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.models.ReceivedSykmelding
import no.nav.sykdig.shared.securelog
import no.nav.sykdig.shared.ValidationResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono


@Component
class RegelClient(
    @Value("\${regel.url}") private val regelUrl: String,
    private val regeltM2mWebClient: WebClient
) {
    val log = applog()
    val securelog = securelog()

    suspend fun valider(sykmelding: ReceivedSykmelding, msgId: String): ValidationResult {
        log.info("validating against rules {}", kv("sykmeldingId", sykmelding.sykmelding.id))

        val response = try {
            regeltM2mWebClient.post()
                .uri("$regelUrl/api/v2/rules/validate")
                .header("Nav-CallId", msgId)
                .bodyValue(sykmelding)
                .retrieve()
                .onStatus({ it != HttpStatus.OK }) { response ->
                    Mono.error(HttpClientErrorException(HttpStatus.NOT_FOUND, "regelvalidering feilet for sykmeldingId ${sykmelding.sykmelding.id}"))
                }
                .bodyToMono(ValidationResult::class.java)
                .awaitSingle()
        } catch (e: Exception) {
            log.error("Validation against rules failed for sykmeldingId: ${sykmelding.sykmelding.id}, error: ${e.message}", e)
            throw e
        }

        return response
    }
}

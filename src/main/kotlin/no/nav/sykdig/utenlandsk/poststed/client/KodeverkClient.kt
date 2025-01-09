package no.nav.sykdig.utenlandsk.poststed.client

import kotlinx.coroutines.reactor.awaitSingle
import no.nav.sykdig.shared.applog
import no.nav.sykdig.utenlandsk.poststed.PostInformasjon
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.UUID

@Component
class KodeverkClient(
    @Value("\${kodeverk.url}") private val url: String,
    private val kodeverkWebClient: WebClient,
) {
    val log = applog()

    @Retryable
    suspend fun hentKodeverk(callId: UUID): List<PostInformasjon> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            this["Nav-Call-Id"] = callId.toString()
            this["Nav-Consumer-Id"] = "syk-dig-backend"
        }

        try {
            val response = kodeverkWebClient.get()
                .uri("$url/api/v1/kodeverk/Postnummer/koder/betydninger?ekskluderUgyldige=true&oppslagsdato=${LocalDate.now()}&spraak=nb")
                .headers { it.addAll(headers) }
                .retrieve()
                .onStatus({ status -> !status.is2xxSuccessful }) { response ->
                    Mono.error(Exception("Error response: ${response.statusCode()}"))
                }
                .bodyToMono(GetKodeverkKoderBetydningerResponse::class.java)
                .awaitSingle()

            return response.toPostInformasjonListe() ?: throw RuntimeException("Ingen respons fra kodeverk")
        } catch (e: Exception) {
            log.error("Noe gikk galt ved henting av postinformasjon fra kodeverk: ${e.message}", e)
            throw RuntimeException("Noe gikk galt ved henting av postinformasjon fra kodeverk")
        }
    }

}

data class GetKodeverkKoderBetydningerResponse(
    val betydninger: Map<String, List<Betydning>>,
) {
    fun toPostInformasjonListe(): List<PostInformasjon> {
        return betydninger.map {
            PostInformasjon(
                postnummer = it.key,
                poststed = it.value.first().beskrivelser["nb"]?.term ?: throw RuntimeException("Kode ${it.key} mangler term"),
            )
        }
    }
}

data class Betydning(
    val beskrivelser: Map<String, Beskrivelse>,
)

data class Beskrivelse(
    val term: String,
)

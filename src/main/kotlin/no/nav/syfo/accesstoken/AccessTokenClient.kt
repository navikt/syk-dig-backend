package no.nav.syfo.accesstoken

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.sykdig.applog
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class AccessTokenClient(
    private val aadAccessTokenUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val webClient: WebClient,
) {
    private val tokenMap = ConcurrentHashMap<String, AadAccessTokenMedExpiry>()
    val logger = applog()

    fun getAccessToken(resource: String): Mono<String> {
        val omToMinutter = Instant.now().plusSeconds(120L)
        val existingToken = tokenMap[resource]

        if (existingToken != null && !existingToken.expiresOn.isBefore(omToMinutter)) {
            return Mono.just(existingToken.access_token)
        }

        logger.debug("Fetching new token from Azure AD")

        return webClient.post()
            .uri(aadAccessTokenUrl)
            .header("Content-type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .bodyValue(
                "client_id=$clientId&scope=$resource&grant_type=client_credentials&client_secret=$clientSecret",
            )
            .retrieve()
            .bodyToMono(AadAccessTokenV2::class.java)
            .doOnNext { response ->
                val tokenMedExpiry =
                    AadAccessTokenMedExpiry(
                        access_token = response.access_token,
                        expires_in = response.expires_in,
                        expiresOn = Instant.now().plusSeconds(response.expires_in.toLong()),
                    )
                tokenMap[resource] = tokenMedExpiry
                logger.debug("Successfully fetched access token")
            }
            .map { it.access_token }
            .onErrorMap { error ->
                logger.error("Failed to fetch access token", error)
                error
            }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AadAccessTokenV2(
    val access_token: String,
    val expires_in: Int,
)

data class AadAccessTokenMedExpiry(
    val access_token: String,
    val expires_in: Int,
    val expiresOn: Instant,
)

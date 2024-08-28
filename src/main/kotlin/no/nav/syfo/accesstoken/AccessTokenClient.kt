package no.nav.syfo.accesstoken

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.sykdig.applog
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

class AccessTokenClient(
    private val aadAccessTokenUrl: String,
    private val clientId: String,
    private val clientSecret: String,
    private val webClient: WebClient,
) {
    @Volatile
    private var tokenMap = HashMap<String, AadAccessTokenMedExpiry>()
    val logger = applog()

    fun getAccessToken(resource: String): String {
        val omToMinutter = Instant.now().plusSeconds(120L)
        return {
            tokenMap[resource]?.takeUnless { it.expiresOn.isBefore(omToMinutter) }
                ?: run {
                    logger.debug("Henter nytt token fra Azure AD")

                    // Make a synchronous (blocking) call using WebClient
                    val response: AadAccessTokenV2 =
                        webClient.post()
                            .uri(aadAccessTokenUrl)
                            .header("Content-type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .bodyValue(
                                "client_id=$clientId&scope=$resource&grant_type=client_credentials&client_secret=$clientSecret",
                            )
                            .retrieve()
                            .bodyToMono(AadAccessTokenV2::class.java)
                            .block()

                    val tokenMedExpiry =
                        AadAccessTokenMedExpiry(
                            access_token = response.access_token,
                            expires_in = response.expires_in,
                            expiresOn = Instant.now().plusSeconds(response.expires_in.toLong()),
                        )

                    tokenMap[resource] = tokenMedExpiry
                    logger.debug("Har hentet accesstoken")
                    tokenMedExpiry
                }
        }.toString()
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

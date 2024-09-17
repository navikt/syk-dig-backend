package no.nav.sykdig.config
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sykdig.applog
import org.springframework.stereotype.Service

@Service
class M2MTokenService(
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    private val clientConfigurationProperties: ClientConfigurationProperties,
) {
    val log = applog()
    fun getOppgaveM2MToken(): String {
        clientConfigurationProperties.registration.forEach { (key, value) ->
            log.info("Client registration found: $key")
        }

        val clientProperties =
            clientConfigurationProperties.registration["oppgave-m2m"]
                ?: throw RuntimeException("Client properties for 'oppgave-m2m' not found")

        val accessTokenResponse = oAuth2AccessTokenService.getAccessToken(clientProperties)

        return accessTokenResponse.accessToken ?: throw RuntimeException("Failed to retrieve M2M access token")
    }
}

package no.nav.sykdig.config
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.stereotype.Service

@Service
class M2MTokenService(
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    private val clientConfigurationProperties: ClientConfigurationProperties
) {

    fun getOppgaveM2MToken(): String {
        val clientProperties = clientConfigurationProperties.registration["oppgave-m2m"]
            ?: throw RuntimeException("Client properties for 'oppgave-m2m' not found")

        val accessTokenResponse = oAuth2AccessTokenService.getAccessToken(clientProperties)

        return accessTokenResponse.accessToken ?: throw RuntimeException("Failed to retrieve M2M access token")
    }
}
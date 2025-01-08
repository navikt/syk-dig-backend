package no.nav.sykdig.shared.config
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service

@Configuration
@EnableConfigurationProperties(ClientConfigurationProperties::class)
class ClientPropertiesConfig

@Service
class M2MTokenService(
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    private val clientConfigurationProperties: ClientConfigurationProperties,
) {
    fun getM2MToken(type: String): String {
        val clientProperties =
            clientConfigurationProperties.registration[type]
                ?: throw RuntimeException("Client properties for $type not found")

        val accessTokenResponse = oAuth2AccessTokenService.getAccessToken(clientProperties)
        return accessTokenResponse.accessToken ?: throw RuntimeException("Failed to retrieve M2M access token")
    }
}

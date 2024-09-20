package no.nav.sykdig.config
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.sykdig.securelog
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
    val securelog = securelog()

    fun getOppgaveM2mToken(): String {
        clientConfigurationProperties.registration.forEach { (key, value) ->
            securelog.info("Client registration found: $key")
        }

        val clientProperties =
            clientConfigurationProperties.registration["oppgave-m2m"]
                ?: throw RuntimeException("Client properties for 'oppgave-m2m' not found")

        securelog.info("Client registration found $clientProperties")

        val accessTokenResponse = oAuth2AccessTokenService.getAccessToken(clientProperties)

        return accessTokenResponse.accessToken ?: throw RuntimeException("Failed to retrieve M2M access token")
    }

    fun getSafM2mToken(): String {
        clientConfigurationProperties.registration.forEach { (key, value) ->
            securelog.info("Client registration found: $key")
        }

        val clientProperties =
            clientConfigurationProperties.registration["saf-m2m"]
                ?: throw RuntimeException("Client properties for 'saf-m2m' not found")

        securelog.info("Client registration found $clientProperties")

        val accessTokenResponse = oAuth2AccessTokenService.getAccessToken(clientProperties)

        return accessTokenResponse.accessToken ?: throw RuntimeException("Failed to retrieve M2M access token for SAF")
    }
}

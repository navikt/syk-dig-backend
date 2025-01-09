package no.nav.sykdig.shared.config

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.sykdig.shared.applog
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@EnableOAuth2Client(cacheEnabled = true)
@Configuration
class AadWebClientConfiguration {

    val log = applog()

    @Bean
    fun istilgangskontrollWebClient(
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): WebClient =
        downstreamWebClient(
            registrationName = "onbehalfof-istilgangskontroll",
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService
        )

    @Bean
    fun safWebClient(
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): WebClient =
        downstreamWebClient(
            registrationName = "onbehalfof-saf",
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService
        )

    @Bean
    fun pdlWebClient(
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): WebClient =
        downstreamWebClient(
            registrationName = "onbehalfof-pdl",
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService
        )

    @Bean
    fun dokarkivWebClient(
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): WebClient =
        downstreamWebClient(
            registrationName = "onbehalfof-dokarkiv",
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService
        )

    @Bean
    fun oppgaveWebClient(
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): WebClient =
        downstreamWebClient(
            registrationName = "onbehalfof-oppgave",
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService
        )

    @Bean
    fun smregisteringWebClient(
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): WebClient =
        downstreamWebClient(
            registrationName = "onbehalfof-smreg",
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService
        )

    private fun downstreamWebClient(
        registrationName: String,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): WebClient {
        val clientProperties =
            clientConfigurationProperties.registration[registrationName]
                ?: throw RuntimeException("Fant ikke config for $registrationName")

        return WebClient.builder()
            .baseUrl(clientProperties.resourceUrl.toString())
            .filter(bearerTokenFilter(clientProperties, oAuth2AccessTokenService))
            .build()
    }

    private fun bearerTokenFilter(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService
    ): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { clientRequest ->
            val token = oAuth2AccessTokenService.getAccessToken(clientProperties).accessToken
            val updatedRequest = ClientRequest.from(clientRequest)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .build()
            Mono.just(updatedRequest)
        }
    }
}

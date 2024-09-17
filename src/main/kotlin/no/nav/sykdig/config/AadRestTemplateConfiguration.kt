package no.nav.sykdig.config

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.context.JwtBearerTokenResolver
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.spring.SpringTokenValidationContextHolder
import no.nav.sykdig.applog
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Primary
@Component
class SykDigTokenResolver : JwtBearerTokenResolver {
    val log = applog()
    override fun token(): String? {
      
        val autentication = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        log.info("Henter auth token" + autentication.token?.tokenValue)
        return autentication.token.tokenValue
    }
}

@EnableOAuth2Client(cacheEnabled = true)
@Configuration
class AadRestTemplateConfiguration {
    val log = applog()

    @Bean
    fun istilgangskontrollRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): RestTemplate =
        downstreamRestTemplate(
            registrationName = "onbehalfof-istilgangskontroll",
            restTemplateBuilder = restTemplateBuilder,
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService,
        )

    @Bean
    fun tokenValidationContextHolder(): TokenValidationContextHolder {
        return SpringTokenValidationContextHolder()
    }

    @Bean
    fun safRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): RestTemplate =
        downstreamRestTemplate(
            registrationName = "onbehalfof-saf",
            restTemplateBuilder = restTemplateBuilder,
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService,
        )

    @Bean
    fun pdlRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): RestTemplate =
        downstreamRestTemplate(
            registrationName = "onbehalfof-pdl",
            restTemplateBuilder = restTemplateBuilder,
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService,
        )

    @Bean
    fun dokarkivRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): RestTemplate {
        val restTemplate =
            downstreamRestTemplate(
                registrationName = "onbehalfof-dokarkiv",
                restTemplateBuilder = restTemplateBuilder,
                clientConfigurationProperties = clientConfigurationProperties,
                oAuth2AccessTokenService = oAuth2AccessTokenService,
            )
        // Bruker HttpComponentsClientHttpRequestFactory til requests for å støtte PATCH.
        val httpClient: CloseableHttpClient = HttpClients.createDefault()
        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)

        restTemplate.requestFactory = requestFactory
        return restTemplate
    }

    @Bean
    fun oppgaveRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): RestTemplate =
        downstreamRestTemplate(
            registrationName = "onbehalfof-oppgave",
            restTemplateBuilder = restTemplateBuilder,
            clientConfigurationProperties = clientConfigurationProperties,
            oAuth2AccessTokenService = oAuth2AccessTokenService,
        )

    private fun downstreamRestTemplate(
        restTemplateBuilder: RestTemplateBuilder,
        clientConfigurationProperties: ClientConfigurationProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
        registrationName: String,
    ): RestTemplate {
        log.info(" Init DownStreamRestTemplate")
        val clientProperties =
            clientConfigurationProperties.registration[registrationName]
                ?: throw RuntimeException("Fant ikke config for $registrationName")
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor(clientProperties, oAuth2AccessTokenService))
            .build()
    }

    private fun bearerTokenInterceptor(
        clientProperties: ClientProperties,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
    ): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
            log.info("Fetched OAuth2 token: ${response.accessToken}")
            request.headers.setBearerAuth(response.accessToken!!)
            execution.execute(request, body)
        }
    }
}

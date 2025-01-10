package no.nav.sykdig.shared.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Configuration
class M2MRestTemplate(
    private val restTemplateBuilder: RestTemplateBuilder,
    private val m2mTokenService: M2MTokenService,
) {
    @Bean
    fun kodeverkRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor("kodeverk-m2m"))
            .build()
    }

    @Bean
    fun oppgaveWebClient(): WebClient {
        return webClientBuilder()
            .filter(bearerTokenExchangeFilterFunction("oppgave-m2m"))
            .build()
    }

    @Bean
    fun oppgaveM2mRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor("oppgave-m2m"))
            .build()
    }

    @Bean
    fun safM2mRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor("saf-m2m"))
            .build()
    }

    @Bean
    fun helsenettM2mRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor("helsenett-m2m"))
            .build()
    }

    @Bean
    fun smtssM2mRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor("smtss-m2m"))
            .build()
    }

    @Bean
    fun regeltM2mRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor("regel-m2m"))
            .build()
    }

    private fun bearerTokenInterceptor(type: String): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val token = m2mTokenService.getM2MToken(type)
            request.headers.setBearerAuth(token)
            execution.execute(request, body)
        }
    }

    fun bearerTokenExchangeFilterFunction(service: String): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { clientRequest ->
            Mono.defer {
                val token = retrieveBearerTokenForService(service)
                val updatedRequest = ClientRequest.from(clientRequest)
                    .header("Authorization", "Bearer $token")
                    .build()
                Mono.just(updatedRequest)
            }
        }
    }

    private fun retrieveBearerTokenForService(service: String): String {
        return "mocked-token-for-$service"
    }

    private fun webClientBuilder(): WebClient.Builder {
        return WebClient.builder()
    }
}

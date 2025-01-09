package no.nav.sykdig.shared.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Configuration
class M2MWebClient(
    private val m2mTokenService: M2MTokenService,
) {
    @Bean
    fun kodeverkWebClient(): WebClient {
        return webClientBuilder()
            .filter(bearerTokenExchangeFilterFunction("kodeverk-m2m"))
            .build()
    }

    @Bean
    fun oppgaveWebClient(): WebClient {
        return webClientBuilder()
            .filter(bearerTokenExchangeFilterFunction("oppgave-m2m"))
            .build()
    }

    @Bean
    fun safM2mWebClient(): WebClient {
        return webClientBuilder()
            .filter(bearerTokenExchangeFilterFunction("saf-m2m"))
            .build()
    }

    @Bean
    fun helsenettM2mWebClient(): WebClient {
        return webClientBuilder()
            .filter(bearerTokenExchangeFilterFunction("helsenett-m2m"))
            .build()
    }

    @Bean
    fun smtssM2mWebClient(): WebClient {
        return webClientBuilder()
            .filter(bearerTokenExchangeFilterFunction("smtss-m2m"))
            .build()
    }

    @Bean
    fun regeltM2mWebClient(): WebClient {
        return webClientBuilder()
            .filter(bearerTokenExchangeFilterFunction("regel-m2m"))
            .build()
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

    private fun bearerTokenInterceptor(type: String): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val token = m2mTokenService.getM2MToken(type)
            request.headers.setBearerAuth(token)
            execution.execute(request, body)
        }
    }

    private fun webClientBuilder(): WebClient.Builder {
        return WebClient.builder()
    }
}

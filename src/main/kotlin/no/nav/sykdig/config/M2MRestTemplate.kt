package no.nav.sykdig.config

import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestTemplate

@Configuration
class M2MRestTemplate(
    private val restTemplateBuilder: RestTemplateBuilder,
    private val m2mTokenService: M2MTokenService,
) {
    @Bean
    fun oppgaveM2mRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptorOppgave())
            .build()
    }

    private fun bearerTokenInterceptorOppgave(): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val token = m2mTokenService.getOppgaveM2mToken()
            request.headers.setBearerAuth(token)
            execution.execute(request, body)
        }
    }

    @Bean
    fun safM2mRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptorSaf())
            .build()
    }

    private fun bearerTokenInterceptorSaf(): ClientHttpRequestInterceptor {
        return ClientHttpRequestInterceptor { request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution ->
            val token = m2mTokenService.getSafM2mToken()
            request.headers.setBearerAuth(token)
            execution.execute(request, body)
        }
    }
}

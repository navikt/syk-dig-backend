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
    fun kodeverkRestTemplate(): RestTemplate {
        return restTemplateBuilder
            .additionalInterceptors(bearerTokenInterceptor("kodeverk-m2m"))
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
}

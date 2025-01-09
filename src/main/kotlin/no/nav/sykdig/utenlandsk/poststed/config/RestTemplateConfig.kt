package no.nav.sykdig.utenlandsk.poststed.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders

import org.springframework.web.reactive.function.client.WebClient


@Configuration
class WebClientConfig {

    @Bean
    fun plainTextUtf8WebClient(): WebClient {
        return WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8")
            .codecs { configurer ->
                configurer.defaultCodecs().multipartCodecs()
            }
            .build()
    }
}

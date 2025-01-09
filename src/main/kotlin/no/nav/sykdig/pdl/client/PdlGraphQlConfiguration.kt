package no.nav.sykdig.pdl.client

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.HttpResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class PdlGraphQlConfiguration {

    @Bean
    fun pdlGraphQlClient(
        @Value("\${pdl.url}") pdlUrl: String,
        pdlWebClient: WebClient,
    ): CustomGraphQLClient {
        return GraphQLClient.createCustom(pdlUrl) { url, _, body ->
            val httpHeaders = HttpHeaders()
            httpHeaders["TEMA"] = "SYM"
            httpHeaders["Behandlingsnummer"] = "B229"
            httpHeaders.contentType = MediaType.APPLICATION_JSON

            val response = pdlWebClient.post()
                .uri(url)
                .headers { it.addAll(httpHeaders) }
                .bodyValue(body)
                .retrieve()
                .toEntity(String::class.java)
                .block()

            HttpResponse(response?.statusCode?.value() ?: 500, response?.body)
        }
    }
}

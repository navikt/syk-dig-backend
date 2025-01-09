package no.nav.sykdig.saf.graphql

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.HttpResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SafGraphQlConfiguration {
    @Bean
    fun safM2mGraphQlClient(
        @Value("\${saf.url}") safUrl: String,
        safM2mWebClient: WebClient,
    ): CustomGraphQLClient {
        return graphQLClient(safUrl, safM2mWebClient)
    }

    @Bean
    fun safGraphQlClient(
        @Value("\${saf.url}") safUrl: String,
        safWebClient: WebClient,
    ): CustomGraphQLClient {
        return graphQLClient(safUrl, safWebClient)
    }

    private fun graphQLClient(
        safUrl: String,
        safWebClient: WebClient
    ): CustomGraphQLClient {
        return GraphQLClient.createCustom(safUrl) { url, _, body ->
            val response = safWebClient.post()
                .uri("$url/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toEntity(String::class.java)
                .block() // block() makes it a synchronous call (can be replaced with a non-blocking approach if needed)

            // Assuming HttpResponse is a custom class that encapsulates the status code and body
            HttpResponse(response.statusCode.value(), response.body)
        }
    }
}

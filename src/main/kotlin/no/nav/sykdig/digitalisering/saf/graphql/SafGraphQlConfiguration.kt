package no.nav.sykdig.digitalisering.saf.graphql

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import com.netflix.graphql.dgs.client.GraphQLClient
import com.netflix.graphql.dgs.client.HttpResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate

@Configuration
class SafGraphQlConfiguration {

    @Bean
    fun safGraphQlClient(
        @Value("\${saf.url}") safUrl: String,
        safRestTemplate: RestTemplate
    ): CustomGraphQLClient {
        return GraphQLClient.createCustom(safUrl) { url, _, body ->
            val httpHeaders = HttpHeaders()
            httpHeaders.contentType = MediaType.APPLICATION_JSON

            val response =
                safRestTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)

            HttpResponse(response.statusCodeValue, response.body)
        }
    }
}

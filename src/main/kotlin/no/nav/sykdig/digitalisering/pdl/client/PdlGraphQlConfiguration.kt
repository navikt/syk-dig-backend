package no.nav.sykdig.digitalisering.pdl.client

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
class PdlGraphQlConfiguration {
    @Bean
    fun pdlGraphQlClient(
        @Value("\${pdl.url}") pdlUrl: String,
        pdlRestTemplate: RestTemplate,
    ): CustomGraphQLClient {
        return GraphQLClient.createCustom(pdlUrl) { url, _, body ->
            val httpHeaders = HttpHeaders()
            httpHeaders["TEMA"] = "SYM"
            httpHeaders.contentType = MediaType.APPLICATION_JSON

            val response =
                pdlRestTemplate.exchange(url, HttpMethod.POST, HttpEntity(body, httpHeaders), String::class.java)

            HttpResponse(response.statusCode.value(), response.body)
        }
    }
}

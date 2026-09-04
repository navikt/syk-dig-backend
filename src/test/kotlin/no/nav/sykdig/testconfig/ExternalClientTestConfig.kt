package no.nav.sykdig.testconfig

import com.netflix.graphql.dgs.client.CustomGraphQLClient
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.client.RestTemplate

/**
 * Erstatter de OAuth-baserte klientene med trygge testbønner, slik at applikasjonskonteksten kan
 * lastes uten client registration for nedstrøms tjenester.
 */
@TestConfiguration
class ExternalClientTestConfig {
    @Bean fun tilgangskontrollRestTemplate(): RestTemplate = RestTemplate()

    @Bean fun safRestTemplate(): RestTemplate = RestTemplate()

    @Bean fun dokarkivRestTemplate(): RestTemplate = RestTemplate()

    @Bean fun oppgaveRestTemplate(): RestTemplate = RestTemplate()

    @Bean fun pdlGraphQlClient(): CustomGraphQLClient = mockk(relaxed = true)
}

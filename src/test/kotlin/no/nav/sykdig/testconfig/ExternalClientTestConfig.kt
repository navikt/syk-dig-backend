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
    @Bean fun tilgangskontrollRestTemplate(): RestTemplate = mockk(relaxed = true)

    @Bean fun safRestTemplate(): RestTemplate = mockk(relaxed = true)

    @Bean fun dokarkivRestTemplate(): RestTemplate = mockk(relaxed = true)

    @Bean fun oppgaveRestTemplate(): RestTemplate = mockk(relaxed = true)

    @Bean fun pdlGraphQlClient(): CustomGraphQLClient = mockk(relaxed = true)
}

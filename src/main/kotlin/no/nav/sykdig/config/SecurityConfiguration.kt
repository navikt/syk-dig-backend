package no.nav.sykdig.config

import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import no.nav.security.token.support.filter.JwtTokenValidationFilter
import no.nav.sykdig.logger
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import java.net.URL


@Configuration
class SecurityConfiguration() {

    private val log = logger()
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain? {

       return  http.authorizeRequests { authorizeRequests ->
                authorizeRequests
                    .mvcMatchers(HttpMethod.GET, "/internal/**").permitAll()
                    .anyRequest().authenticated()
                    .and()
                    .oauth2ResourceServer()
                    .jwt()
            }
            .csrf().disable()
            .build()
    }
}

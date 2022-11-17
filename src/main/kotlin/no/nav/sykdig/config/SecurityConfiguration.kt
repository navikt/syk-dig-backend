package no.nav.sykdig.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfiguration() {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain? {
        return http.authorizeRequests { authorizeRequests ->
            authorizeRequests
                .mvcMatchers(HttpMethod.GET, "/internal/**").permitAll()
                .mvcMatchers(HttpMethod.GET, "/schema.json").permitAll()
                .anyRequest().authenticated()
                .and()
                .oauth2ResourceServer()
                .jwt()
        }
            .headers().frameOptions().sameOrigin().and()
            .csrf().disable()
            .build()
    }
}

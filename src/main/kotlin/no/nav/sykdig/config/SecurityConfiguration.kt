package no.nav.sykdig.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfiguration() {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain? {
        return http.authorizeHttpRequests { authorizeRequests ->
            authorizeRequests
                .requestMatchers(HttpMethod.GET, "/internal/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/schema.json").permitAll()
                .anyRequest().authenticated()
        }
            .oauth2ResourceServer { it.jwt {} }
            .headers { headersConfigurer ->
                headersConfigurer.frameOptions { frameOptionsCustomizer ->
                    frameOptionsCustomizer.sameOrigin()
                }
            }
            .build()
    }
}

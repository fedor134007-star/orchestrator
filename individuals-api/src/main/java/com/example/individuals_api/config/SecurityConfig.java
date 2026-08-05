package com.example.individuals_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())

                .requestCache(requestCache ->
                        requestCache.requestCache(NoOpServerRequestCache.getInstance()))

                .authorizeExchange(exchanges -> exchanges
                        // Swagger
                        .pathMatchers("/swagger-ui.html").permitAll()
                        .pathMatchers("/swagger-ui/**").permitAll()
                        .pathMatchers("/api-docs/**").permitAll()
                        .pathMatchers("/api-docs").permitAll()
                        .pathMatchers("/swagger-resources/**").permitAll()
                        .pathMatchers("/webjars/**").permitAll()
                        // Auth
                        .pathMatchers("/api/v1/auth/registration").permitAll()
                        .pathMatchers("/api/v1/auth/login").permitAll()
                        .pathMatchers("/api/v1/auth/refresh-token").permitAll()
                        .pathMatchers("/api/v1/auth/me").authenticated()
                        // Actuator
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/actuator/prometheus").permitAll()


                        .anyExchange().denyAll()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )

                .build();
    }

    /**
     * Конвертер для извлечения ролей из JWT токена Keycloak
     */
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");

        ReactiveJwtAuthenticationConverter jwtConverter =
                new ReactiveJwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(
                new ReactiveJwtGrantedAuthoritiesConverterAdapter(grantedAuthoritiesConverter)
        );

        return jwtConverter;
    }
}
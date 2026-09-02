package com.example.persons_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtGrantedAuthoritiesConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

@Profile("!test")
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

                        // Actuator
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/actuator/prometheus").permitAll()

                        // Persons API - защищенные эндпоинты
                        .pathMatchers(HttpMethod.POST, "/api/v1/persons/registration").authenticated()
                        .pathMatchers(HttpMethod.GET, "/api/v1/persons/**").authenticated()
                        .pathMatchers(HttpMethod.PUT, "/api/v1/persons/**").authenticated()
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/persons/**").authenticated()

                        // Все остальные запросы запрещены
                        .anyExchange().denyAll()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )

                .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        grantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles");

        ReactiveJwtAuthenticationConverter jwtConverter = new ReactiveJwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new ReactiveJwtGrantedAuthoritiesConverterAdapter(grantedAuthoritiesConverter));

        return jwtConverter;
    }
}
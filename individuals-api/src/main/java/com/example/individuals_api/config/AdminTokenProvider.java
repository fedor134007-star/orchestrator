package com.example.individuals_api.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Component
public class AdminTokenProvider {

    private final WebClient keycloakWebClient;
    private final String clientId;
    private final String clientSecret;
    private final String realm;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.MIN;

    public AdminTokenProvider(
            @Qualifier("keycloakWebClient") WebClient keycloakWebClient,
            @Value("${keycloak.client-id}") String clientId,
            @Value("${keycloak.client-secret}") String clientSecret,
            @Value("${keycloak.realm}") String realm) {
        this.keycloakWebClient = keycloakWebClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.realm = realm;
    }

    public Mono<String> getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return Mono.just(cachedToken);
        }
        return fetchNewToken();
    }

    private Mono<String> fetchNewToken() {
        return keycloakWebClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                        .with("client_id", clientId)
                        .with("client_secret", clientSecret))
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(response -> {
                    long expiresIn = ((Number) response.get("expires_in")).longValue();
                    this.cachedToken = (String) response.get("access_token");
                    this.tokenExpiry = Instant.now().plusSeconds(expiresIn - 10);
                })
                .map(response -> (String) response.get("access_token"));
    }
}
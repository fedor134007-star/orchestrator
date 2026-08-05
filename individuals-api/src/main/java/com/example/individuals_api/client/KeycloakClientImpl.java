package com.example.individuals_api.client;

import com.example.individuals_api.config.AdminTokenProvider;
import com.example.individuals_api.config.KeycloakProperties;
import com.example.individuals_api.dto.KeycloakUserRequest;
import lombok.RequiredArgsConstructor;
import net.generated.individuals.dto.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KeycloakClientImpl implements KeycloakClient {

    private final AdminTokenProvider tokenProvider;
    private final WebClient keycloakWebClient;
    private final KeycloakProperties properties;


    @Override
    public Mono<String> register(RegistrationRequest registrationRequest) {
        return tokenProvider.getToken()
                .flatMap(token -> {
                    KeycloakUserRequest body = KeycloakUserRequest.from(registrationRequest);
                    return keycloakWebClient.post()
                            .uri("/admin/realms/{realm}/users", "individual")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(body)
                            .retrieve()
                            .toBodilessEntity()
                            .map(r -> {
                                String loc = r.getHeaders().getFirst("Location");
                                return loc.substring(loc.lastIndexOf('/') + 1);
                            })
                            .flatMap(userId -> setPassword(token, userId, registrationRequest.getPassword())
                                    .thenReturn(userId));
                });
    }

    private Mono<Void> setPassword(String adminToken, String userId, String password) {
        Map<String, Object> cred = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );
        return keycloakWebClient.put()
                .uri("/admin/realms/{realm}/users/{userId}/reset-password", "individual", userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(cred)
                .retrieve()
                .bodyToMono(Void.class);
    }


    public Mono<TokenResponse> login(LoginRequest request) {
        return keycloakWebClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", "individual")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "password")
                        .with("client_id", properties.getClientId())
                        .with("client_secret", properties.getClientSecret())
                        .with("username", request.getEmail())
                        .with("password", request.getPassword()))
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> {
                    TokenResponse response = new TokenResponse();
                    response.setAccessToken((String) map.get("access_token"));
                    response.setRefreshToken((String) map.get("refresh_token"));
                    response.setExpiresIn(((Number) map.get("expires_in")).intValue());
                    response.setTokenType((String) map.get("token_type"));
                    return response;
                });
    }

    @Override
    public Mono<TokenResponse> refreshToken(RefreshTokenRequest request) {
        return keycloakWebClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", "individual")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                        .with("client_id", properties.getClientId())
                        .with("client_secret", properties.getClientSecret())
                        .with("refresh_token", request.getRefreshToken()))
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> {
                    TokenResponse response = new TokenResponse();
                    response.setAccessToken((String) map.get("access_token"));
                    response.setRefreshToken((String) map.get("refresh_token"));
                    response.setExpiresIn(((Number) map.get("expires_in")).intValue());
                    response.setTokenType((String) map.get("token_type"));
                    return response;
                });
    }

    @Override
    public Mono<CurrentUserResponse> getCurrentUser(String userId) {
        return tokenProvider.getToken()
                .flatMap(token ->
                        keycloakWebClient.get()
                                .uri("/admin/realms/{realm}/users/{userId}", "individual", userId)
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .map(user -> {
                                    CurrentUserResponse response = new CurrentUserResponse();
                                    response.setEmail((String) user.get("email"));
                                    response.setFirstName((String) user.get("firstName"));
                                    response.setLastName((String) user.get("lastName"));
                                    response.setStatus(CurrentUserResponse.StatusEnum.ACTIVE);
                                    response.setId(UUID.fromString((String) user.get("id")));

                                    // Время создания
                                    Long created = (Long) user.get("createdTimestamp");
                                    if (created != null) {
                                        response.setCreatedAt(OffsetDateTime.ofInstant(Instant.ofEpochMilli(created), ZoneOffset.UTC));
                                    }
                                    return response;
                                })
                );
    }
}

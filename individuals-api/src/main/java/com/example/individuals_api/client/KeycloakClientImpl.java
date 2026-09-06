package com.example.individuals_api.client;

import com.example.individuals_api.config.AdminTokenProvider;
import com.example.individuals_api.config.KeycloakProperties;
import com.example.individuals_api.dto.KeycloakUserRequest;
import com.example.individuals_api.exception.PartialRegistrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.generated.individuals.dto.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Component
@RequiredArgsConstructor
public class KeycloakClientImpl implements KeycloakClient {

    private final AdminTokenProvider tokenProvider;
    private final WebClient keycloakWebClient;
    private final KeycloakProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<String> register(RegistrationRequest registrationRequest) {
        String email = registrationRequest.getEmail();
        String operationId = UUID.randomUUID().toString();

        log.info("Starting Keycloak registration, operationId: {}, email: {}", operationId, email);

        return tokenProvider.getToken()
                .flatMap(token -> registerUser(registrationRequest, token, operationId));
    }

    private Mono<String> registerUser(RegistrationRequest request, String token, String operationId) {
        KeycloakUserRequest body = KeycloakUserRequest.from(request);

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
                .flatMap(userId -> setPasswordWithRollback(token, userId, request.getPassword(), operationId))
                .onErrorResume(WebClientResponseException.Conflict.class, e -> {
                    log.warn("User already exists, attempting to continue registration, operationId: {}, email: {}",
                            operationId, request.getEmail());
                    return findUserByEmail(request.getEmail(), token)
                            .flatMap(userId -> setPasswordWithRollback(token, userId, request.getPassword(), operationId));
                })
                .onErrorResume(e -> {
                    log.error("Registration failed, operationId: {}, email: {}, error: {}",
                            operationId, request.getEmail(), e.getMessage());
                    return Mono.error(new PartialRegistrationException(
                            "Failed to register user in Keycloak", e));
                });
    }

    private Mono<String> setPasswordWithRollback(String adminToken, String userId, String password, String operationId) {
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
                .toBodilessEntity()
                .thenReturn(userId)
                .onErrorResume(e -> {
                    log.error("Failed to set password, rolling back user creation, operationId: {}, userId: {}, error: {}",
                            operationId, userId, e.getMessage());
                    return deleteUser(adminToken, userId)
                            .then(Mono.error(new PartialRegistrationException(
                                    "Failed to set password, user rolled back", e)));
                });
    }

    private Mono<Void> deleteUser(String adminToken, String userId) {
        return keycloakWebClient.delete()
                .uri("/admin/realms/{realm}/users/{userId}", "individual", userId)
                .header("Authorization", "Bearer " + adminToken)
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(e -> {
                    log.warn("Failed to delete user during rollback, userId: {}, error: {}", userId, e.getMessage());
                    return Mono.empty(); // Продолжаем, даже если удаление не удалось
                });
    }

    private Mono<String> findUserByEmail(String email, String token) {
        return keycloakWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin/realms/{realm}/users")
                        .queryParam("email", email)
                        .build("individual"))
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(List.class)
                .flatMap(users -> {
                    if (users != null && !users.isEmpty()) {
                        Map<String, Object> user = (Map<String, Object>) users.get(0);
                        return Mono.just((String) user.get("id"));
                    }
                    return Mono.empty();
                });
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

                    // Декодируем JWT вручную
                    String accessToken = (String) map.get("access_token");
                    if (accessToken != null) {
                        Map<String, Object> claims = decodeJwt(accessToken);

                        response.setKeycloakUserId((String) claims.get("sub"));
                        response.setEmailVerified(Boolean.TRUE.equals(claims.get("email_verified")));
                        response.setRoles(extractRoles(claims));
                    }

                    return response;
                });
    }

    private Map<String, Object> decodeJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                return objectMapper.readValue(payload, Map.class);
            }
        } catch (Exception e) {
            log.error("Failed to decode JWT", e);
        }
        return Collections.emptyMap();
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
    public Mono<CurrentUserResponse> getCurrentUser(String keycloakUserId) {
        return tokenProvider.getToken()
                .flatMap(token ->
                        keycloakWebClient.get()
                                .uri("/admin/realms/{realm}/users/{userId}", "individual", keycloakUserId)
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .bodyToMono(Map.class)
                                .map(user -> {
                                    CurrentUserResponse response = new CurrentUserResponse();

                                    // Keycloak ID
                                    response.setKeycloakUserId(keycloakUserId);

                                    // Основные поля
                                    response.setEmail((String) user.get("email"));
                                    response.setFirstName((String) user.get("firstName"));
                                    response.setLastName((String) user.get("lastName"));
                                    response.setStatus(CurrentUserResponse.StatusEnum.ACTIVE);

                                    // Email Verified
                                    Boolean emailVerified = (Boolean) user.get("emailVerified");
                                    response.setEmailVerified(emailVerified != null ? emailVerified : false);

                                    // Роли
                                    List<String> roles = extractRoles(user);
                                    response.setRoles(roles);

                                    // Время создания
                                    Long created = (Long) user.get("createdTimestamp");
                                    if (created != null) {
                                        response.setCreatedAt(OffsetDateTime.ofInstant(
                                                Instant.ofEpochMilli(created), ZoneOffset.UTC));
                                    }

                                    return response;
                                })
                );
    }

    private List<String> extractRoles(Map<String, Object> user) {
        List<String> roles = new ArrayList<>();

        // Realm roles
        Object realmRoles = user.get("realmRoles");
        if (realmRoles instanceof List) {
            roles.addAll((List<String>) realmRoles);
        }

        // Client roles
        Object clientRoles = user.get("clientRoles");
        if (clientRoles instanceof Map) {
            Map<String, Object> clientRolesMap = (Map<String, Object>) clientRoles;
            for (Object value : clientRolesMap.values()) {
                if (value instanceof List) {
                    roles.addAll((List<String>) value);
                }
            }
        }

        // Default role
        if (roles.isEmpty()) {
            roles.add("USER");
        }

        return roles;
    }
}
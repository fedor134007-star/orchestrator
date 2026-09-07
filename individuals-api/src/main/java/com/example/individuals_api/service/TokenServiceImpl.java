package com.example.individuals_api.service;

import com.example.individuals_api.client.KeycloakClient;
import com.example.individuals_api.client.PersonsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.generated.individuals.dto.LoginRequest;
import net.generated.individuals.dto.RefreshTokenRequest;
import net.generated.individuals.dto.TokenResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final KeycloakClient keycloakClient;
    private final PersonsClient personsClient;

    @Override
    public Mono<TokenResponse> login(LoginRequest request) {
        String operationId = UUID.randomUUID().toString();
        log.info("Processing login request, operationId: {}, email: {}", operationId, request.getEmail());

        return keycloakClient.login(request)
                .flatMap(tokenResponse -> {
                    return personsClient.getPersonByEmail(request.getEmail())
                            .map(personData -> {
                                tokenResponse.setUserUid(personData.getId());
                                return tokenResponse;
                            })
                            .switchIfEmpty(Mono.just(tokenResponse));
                });
    }

    @Override
    public Mono<TokenResponse> refresh(RefreshTokenRequest request) {
        String operationId = UUID.randomUUID().toString();
        log.info("Processing refresh token request, operationId: {}", operationId);

        return keycloakClient.refreshToken(request)
                .flatMap(tokenResponse -> {
                    String keycloakUserId = tokenResponse.getKeycloakUserId();
                    if (keycloakUserId != null) {
                        return keycloakClient.getCurrentUser(keycloakUserId)
                                .flatMap(currentUser -> {
                                    String email = currentUser.getEmail();
                                    return personsClient.getPersonByEmail(email)
                                            .map(personData -> {
                                                tokenResponse.setUserUid(personData.getId());
                                                return tokenResponse;
                                            })
                                            .switchIfEmpty(Mono.just(tokenResponse));
                                })
                                .switchIfEmpty(Mono.just(tokenResponse));
                    }
                    return Mono.just(tokenResponse);
                });
    }
}
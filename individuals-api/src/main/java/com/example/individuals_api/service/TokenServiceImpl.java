package com.example.individuals_api.service;

import com.example.individuals_api.client.KeycloakClient;
import lombok.RequiredArgsConstructor;
import net.generated.individualls.dto.LoginRequest;
import net.generated.individualls.dto.RefreshTokenRequest;
import net.generated.individualls.dto.TokenResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private final KeycloakClient keycloakClient;

    public Mono<TokenResponse> login(LoginRequest  request) {
        log.info("Login: {}", request.getEmail());
        return keycloakClient.login(request);
    }

    public Mono<TokenResponse> refresh(RefreshTokenRequest request) {
        log.info("Refresh token: {}", request.getRefreshToken());
        return keycloakClient.refreshToken(request);
    }
}

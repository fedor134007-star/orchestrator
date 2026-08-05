package com.example.individuals_api.service;

import com.example.individuals_api.client.KeycloakClient;
import net.generated.individuals.dto.LoginRequest;
import net.generated.individuals.dto.RefreshTokenRequest;
import net.generated.individuals.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceImplTest {

    @Mock
    private KeycloakClient keycloakClient;

    @InjectMocks
    private TokenServiceImpl tokenService;

    @Test
    void login_shouldReturnTokenResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("Test1234!");

        TokenResponse response = new TokenResponse();
        response.setAccessToken("access-token");
        response.setRefreshToken("refresh-token");
        response.setExpiresIn(300);
        response.setTokenType("Bearer");

        when(keycloakClient.login(request)).thenReturn(Mono.just(response));

        StepVerifier.create(tokenService.login(request))
                .expectNext(response)
                .verifyComplete();

        verify(keycloakClient).login(request);
    }

    @Test
    void login_whenClientFails_shouldPropagateError() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("wrong");

        when(keycloakClient.login(request))
                .thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

        StepVerifier.create(tokenService.login(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void refresh_shouldReturnTokenResponse() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        TokenResponse response = new TokenResponse();
        response.setAccessToken("new-access-token");
        response.setRefreshToken("new-refresh-token");
        response.setExpiresIn(300);
        response.setTokenType("Bearer");

        when(keycloakClient.refreshToken(request)).thenReturn(Mono.just(response));

        StepVerifier.create(tokenService.refresh(request))
                .expectNext(response)
                .verifyComplete();

        verify(keycloakClient).refreshToken(request);
    }

    @Test
    void refresh_whenClientFails_shouldPropagateError() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("expired-token");

        when(keycloakClient.refreshToken(request))
                .thenReturn(Mono.error(new RuntimeException("Token expired")));

        StepVerifier.create(tokenService.refresh(request))
                .expectError(RuntimeException.class)
                .verify();
    }
}
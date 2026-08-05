package com.example.individuals_api.controller;

import net.generated.individuals.dto.*;
import com.example.individuals_api.service.UserService;
import com.example.individuals_api.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private ServerWebExchange exchange;

    @InjectMocks
    private AuthController authController;

    @Test
    void register_shouldReturnTokenResponse() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("test@test.com");
        request.setPassword("Test1234!");
        request.setConfirmPassword("Test1234!");
        request.setFirstName("John");
        request.setLastName("Doe");

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("at");
        tokenResponse.setRefreshToken("rt");
        tokenResponse.setExpiresIn(300);
        tokenResponse.setTokenType("Bearer");

        when(userService.register(any())).thenReturn(Mono.just(tokenResponse));

        StepVerifier.create(authController.register(Mono.just(request), exchange))
                .expectNextMatches(response ->
                        response.getStatusCode().is2xxSuccessful() &&
                                response.getBody() != null)
                .verifyComplete();
    }

    @Test
    void login_shouldReturnTokenResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("Test1234!");

        TokenResponse response = new TokenResponse();
        response.setAccessToken("at");

        when(tokenService.login(any())).thenReturn(Mono.just(response));

        StepVerifier.create(authController.login(Mono.just(request), exchange))
                .expectNext(ResponseEntity.ok(response))
                .verifyComplete();
    }

    @Test
    void refreshToken_shouldReturnTokenResponse() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old");

        TokenResponse response = new TokenResponse();
        response.setAccessToken("new");

        when(tokenService.refresh(any())).thenReturn(Mono.just(response));

        StepVerifier.create(authController.refreshToken(Mono.just(request), exchange))
                .expectNext(ResponseEntity.ok(response))
                .verifyComplete();
    }
}
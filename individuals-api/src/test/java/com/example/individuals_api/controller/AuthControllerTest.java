package com.example.individuals_api.controller;

import net.generated.individualls.dto.*;
import com.example.individuals_api.service.UserService;
import com.example.individuals_api.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

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

        TokenResponse response = new TokenResponse();
        response.setAccessToken("at");
        response.setRefreshToken("rt");
        response.setExpiresIn(300);
        response.setTokenType("Bearer");

        when(userService.register(any())).thenReturn(Mono.just(response));

        StepVerifier.create(authController.register(request))
                .expectNext(response)
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

        StepVerifier.create(authController.login(request))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void refreshToken_shouldReturnTokenResponse() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old");

        TokenResponse response = new TokenResponse();
        response.setAccessToken("new");

        when(tokenService.refresh(any())).thenReturn(Mono.just(response));

        StepVerifier.create(authController.refreshToken(request))
                .expectNext(response)
                .verifyComplete();
    }
}
package com.example.individuals_api.service;

import com.example.individuals_api.client.KeycloakClient;
import com.example.individuals_api.utils.AuthMetrics;
import net.generated.individuals.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private KeycloakClient keycloakClient;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthMetrics metrics;

    @InjectMocks
    private UserServiceImpl userService;

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

        when(keycloakClient.register(request)).thenReturn(Mono.just("user-id"));
        when(tokenService.login(any(LoginRequest.class))).thenReturn(Mono.just(tokenResponse));

        StepVerifier.create(userService.register(request))
                .expectNext(tokenResponse)
                .verifyComplete();

        verify(keycloakClient).register(request);
        verify(tokenService).login(any(LoginRequest.class));
    }

    @Test
    void register_whenKeycloakFails_shouldPropagateError() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("test@test.com");
        request.setPassword("Test1234!");
        request.setConfirmPassword("Test1234!");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(keycloakClient.register(request))
                .thenReturn(Mono.error(new RuntimeException("Keycloak error")));

        StepVerifier.create(userService.register(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void register_whenLoginFails_shouldPropagateError() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("test@test.com");
        request.setPassword("Test1234!");
        request.setConfirmPassword("Test1234!");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(keycloakClient.register(request)).thenReturn(Mono.just("user-id"));
        when(tokenService.login(any(LoginRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Login failed")));

        StepVerifier.create(userService.register(request))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void getCurrentUser_shouldReturnUser() {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setEmail("test@test.com");
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setStatus(CurrentUserResponse.StatusEnum.ACTIVE);

        when(keycloakClient.getCurrentUser("user-id")).thenReturn(Mono.just(response));

        StepVerifier.create(userService.getCurrentUser("user-id"))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void getCurrentUser_whenNotFound_shouldPropagateError() {
        when(keycloakClient.getCurrentUser("unknown-id"))
                .thenReturn(Mono.error(new RuntimeException("User not found")));

        StepVerifier.create(userService.getCurrentUser("unknown-id"))
                .expectError(RuntimeException.class)
                .verify();
    }
}
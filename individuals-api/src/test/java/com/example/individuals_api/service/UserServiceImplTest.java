package com.example.individuals_api.service;

import com.example.individuals_api.client.KeycloakClient;
import com.example.individuals_api.client.PersonsClient;
import com.example.individuals_api.exception.RegistrationException;
import com.example.individuals_api.utils.AuthMetrics;
import net.example.person.dto.UserResponse;
import net.generated.individuals.dto.CurrentUserResponse;
import net.generated.individuals.dto.LoginRequest;
import net.generated.individuals.dto.RegistrationRequest;
import net.generated.individuals.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl: регистрация, вход и компенсация")
class UserServiceImplTest {

    private static final UUID USER_UID = UUID.fromString("6c8ec4d0-6fd5-4db8-bf4d-7bcbdb2f6c02");

    @Mock
    private KeycloakClient keycloakClient;

    @Mock
    private PersonsClient personsClient;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthMetrics metrics;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(keycloakClient, personsClient, tokenService, metrics);
    }

    @Test
    @DisplayName("успешная регистрация: токен получен, компенсация не вызывалась")
    void register_shouldReturnTokenResponse() {
        RegistrationRequest request = request();
        TokenResponse tokenResponse = tokenResponse();

        when(personsClient.registerPerson(request)).thenReturn(Mono.just(personResponse()));
        when(keycloakClient.register(request)).thenReturn(Mono.just("keycloak-id"));
        when(tokenService.login(any(LoginRequest.class))).thenReturn(Mono.just(tokenResponse));

        StepVerifier.create(userService.register(request))
                .expectNext(tokenResponse)
                .verifyComplete();

        verify(personsClient, never()).deletePerson(any());
    }

    @Test
    @DisplayName("сбой регистрации в Keycloak → доменный пользователь откатывается")
    void register_whenKeycloakFails_shouldCompensatePerson() {
        RegistrationRequest request = request();

        when(personsClient.registerPerson(request)).thenReturn(Mono.just(personResponse()));
        when(keycloakClient.register(request))
                .thenReturn(Mono.error(new RuntimeException("Keycloak error")));
        when(personsClient.deletePerson(USER_UID)).thenReturn(Mono.empty());

        StepVerifier.create(userService.register(request))
                .expectError(RegistrationException.class)
                .verify();

        verify(personsClient).deletePerson(USER_UID);
    }

    @Test
    @DisplayName("сбой логина после успешной регистрации в Keycloak НЕ удаляет пользователя")
    void register_whenLoginFails_shouldNotDeletePerson() {
        RegistrationRequest request = request();

        when(personsClient.registerPerson(request)).thenReturn(Mono.just(personResponse()));
        when(keycloakClient.register(request)).thenReturn(Mono.just("keycloak-id"));
        when(tokenService.login(any(LoginRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Login failed")));

        StepVerifier.create(userService.register(request))
                .expectError(RegistrationException.class)
                .verify();

        // Учётная запись Keycloak создана — доменный профиль удалять нельзя.
        verify(personsClient, never()).deletePerson(any());
    }

    @Test
    @DisplayName("сбой компенсации не подменяет исходную ошибку")
    void register_whenCompensationFails_shouldPropagateOriginalError() {
        RegistrationRequest request = request();

        when(personsClient.registerPerson(request)).thenReturn(Mono.just(personResponse()));
        when(keycloakClient.register(request))
                .thenReturn(Mono.error(new RuntimeException("Keycloak error")));
        when(personsClient.deletePerson(USER_UID))
                .thenReturn(Mono.error(new RuntimeException("person-service unavailable")));

        StepVerifier.create(userService.register(request))
                .expectErrorSatisfies(error -> {
                    if (!(error instanceof RegistrationException)) {
                        throw new AssertionError("ожидалась RegistrationException, была " + error);
                    }
                })
                .verify();

        verify(personsClient).deletePerson(USER_UID);
    }

    @Test
    @DisplayName("текущий пользователь: данные Keycloak обогащаются userUid")
    void getCurrentUser_shouldReturnUser() {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setEmail("test@test.com");
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setStatus(CurrentUserResponse.StatusEnum.ACTIVE);

        when(keycloakClient.getCurrentUser("keycloak-id")).thenReturn(Mono.just(response));
        when(personsClient.getPersonByEmail("test@test.com"))
                .thenReturn(Mono.just(personResponse()));

        StepVerifier.create(userService.getCurrentUser("keycloak-id"))
                .assertNext(current -> {
                    if (!USER_UID.equals(current.getUserUid())) {
                        throw new AssertionError("userUid не заполнен из person-service");
                    }
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("текущий пользователь: отсутствие в person-service не ломает ответ")
    void getCurrentUser_whenPersonMissing_shouldReturnKeycloakData() {
        CurrentUserResponse response = new CurrentUserResponse();
        response.setEmail("test@test.com");
        response.setStatus(CurrentUserResponse.StatusEnum.ACTIVE);

        when(keycloakClient.getCurrentUser("keycloak-id")).thenReturn(Mono.just(response));
        when(personsClient.getPersonByEmail("test@test.com")).thenReturn(Mono.empty());

        StepVerifier.create(userService.getCurrentUser("keycloak-id"))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    @DisplayName("сбой Keycloak при чтении профиля пробрасывается")
    void getCurrentUser_whenNotFound_shouldPropagateError() {
        when(keycloakClient.getCurrentUser("unknown-id"))
                .thenReturn(Mono.error(new RuntimeException("User not found")));

        StepVerifier.create(userService.getCurrentUser("unknown-id"))
                .expectError(RuntimeException.class)
                .verify();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private RegistrationRequest request() {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail("test@test.com");
        request.setPassword("Test1234!");
        request.setConfirmPassword("Test1234!");
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }

    private UserResponse personResponse() {
        UserResponse response = new UserResponse();
        response.setId(USER_UID);
        response.setEmail("test@test.com");
        response.setFirstName("John");
        response.setLastName("Doe");
        return response;
    }

    private TokenResponse tokenResponse() {
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken("access");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresIn(300);
        tokenResponse.setTokenType("Bearer");
        return tokenResponse;
    }
}

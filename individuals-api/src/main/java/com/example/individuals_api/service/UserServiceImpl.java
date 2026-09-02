package com.example.individuals_api.service;

import com.example.individuals_api.client.KeycloakClient;
import com.example.individuals_api.client.PersonsClient;
import com.example.individuals_api.exception.PasswordMismatchException;
import com.example.individuals_api.exception.RegistrationException;
import com.example.individuals_api.utils.AuthMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.generated.individuals.dto.CurrentUserResponse;
import net.generated.individuals.dto.LoginRequest;
import net.generated.individuals.dto.RegistrationRequest;
import net.generated.individuals.dto.TokenResponse;
import net.generated.person.dto.PersonResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final KeycloakClient keycloakClient;
    private final PersonsClient personsClient;  // Добавлен
    private final TokenService tokenService;
    private final AuthMetrics metrics;

    @Override
    public Mono<TokenResponse> register(RegistrationRequest request) {
        long start = System.currentTimeMillis();
        log.info("Starting registration for email: {}", request.getEmail());

        // 1. Валидация паролей (до внешних вызовов)
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("Password mismatch for email: {}", request.getEmail());
            metrics.recordRegistration(false, System.currentTimeMillis() - start);
            return Mono.error(new PasswordMismatchException("Passwords do not match"));
        }

        // 2. Создание доменного пользователя в person-service
        return personsClient.registerPerson(request)
                .flatMap(personResponse -> {
                    String userUid = personResponse.getUserUid().toString();
                    log.info("Person created with userUid: {}", userUid);

                    return keycloakClient.register(request)
                            .flatMap(keycloakUserId -> {
                                log.info("User registered in Keycloak with id: {}", keycloakUserId);

                                LoginRequest loginRequest = new LoginRequest();
                                loginRequest.setEmail(request.getEmail());
                                loginRequest.setPassword(request.getPassword());

                                return tokenService.login(loginRequest)
                                        .map(tokenResponse -> {
                                            tokenResponse.setUserUid(UUID.fromString(userUid));
                                            tokenResponse.setKeycloakUserId(keycloakUserId);
                                            return tokenResponse;
                                        });
                            });
                })
                .doOnSuccess(r -> {
                    log.info("Registration completed for email: {}", request.getEmail());
                    metrics.recordRegistration(true, System.currentTimeMillis() - start);
                })
                .doOnError(e -> {
                    log.error("Registration failed for email: {}", request.getEmail(), e);
                    metrics.recordRegistration(false, System.currentTimeMillis() - start);
                })
                .onErrorMap(e -> {
                    if (e instanceof PasswordMismatchException) {
                        return e;
                    }
                    return new RegistrationException("Failed to register user", e);
                });
    }

    @Override
    public Mono<CurrentUserResponse> getCurrentUser(String keycloakUserId) {
        log.info("Getting current user by keycloakUserId: {}", keycloakUserId);

        return keycloakClient.getCurrentUser(keycloakUserId)
                .flatMap(keycloakData -> {
                    String email = keycloakData.getEmail();

                    return personsClient.getPersonByEmail(email)
                            .map(personData -> {
                                keycloakData.setUserUid(personData.getUserUid());
                                return keycloakData;
                            })
                            .switchIfEmpty(Mono.just(keycloakData));
                });
    }
}
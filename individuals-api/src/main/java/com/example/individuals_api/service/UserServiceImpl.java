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

        // 2. Создание доменного пользователя в person-service.
        // Идентификатор из доменной модели person-service передаётся дальше в Keycloak
        // как атрибут user_uid — оркестратор остаётся единственной точкой интеграции с Keycloak.
        return personsClient.registerPerson(request)
                .flatMap(personResponse -> {
                    UUID userUid = personResponse.getId();
                    log.info("Person created with userUid: {}", userUid);

                    // Компенсация относится ТОЛЬКО к шагу регистрации в Keycloak:
                    // если учётная запись в IdP не создана, доменный пользователь «висячий».
                    return compensateOnKeycloakFailure(keycloakClient.register(request), userUid)
                            .flatMap(keycloakUserId -> {
                                log.info("User registered in Keycloak with id: {}", keycloakUserId);

                                // Вход отделён от регистрации: учётная запись Keycloak уже создана,
                                // поэтому сбой выдачи токена не должен удалять доменного пользователя.
                                // Клиент получит ошибку и сможет войти повторно.
                                LoginRequest loginRequest = new LoginRequest();
                                loginRequest.setEmail(request.getEmail());
                                loginRequest.setPassword(request.getPassword());

                                return tokenService.login(loginRequest)
                                        .map(tokenResponse -> {
                                            tokenResponse.setUserUid(userUid);
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

    /**
     * Откатывает доменного пользователя из person-service, если не удалось создать
     * учётную запись в Keycloak.
     *
     * <p>Сбой самой компенсации логируется, но не подменяет исходную ошибку: клиенту
     * важно получить причину неудачной регистрации, а не «не удалось откатить».</p>
     */
    private <T> Mono<T> compensateOnKeycloakFailure(Mono<T> keycloakRegistration, UUID userUid) {
        return keycloakRegistration.onErrorResume(error -> personsClient.deletePerson(userUid)
                .onErrorResume(compensationError -> {
                    log.warn("Не удалось откатить пользователя {} из person-service: {}",
                            userUid, compensationError.getMessage());
                    return Mono.empty();
                })
                .then(Mono.error(error)));
    }

    @Override
    public Mono<CurrentUserResponse> getCurrentUser(String keycloakUserId) {
        log.info("Getting current user by keycloakUserId: {}", keycloakUserId);

        return keycloakClient.getCurrentUser(keycloakUserId)
                .flatMap(keycloakData -> {
                    String email = keycloakData.getEmail();

                    return personsClient.getPersonByEmail(email)
                            .map(personData -> {
                                keycloakData.setUserUid(personData.getId());
                                return keycloakData;
                            })
                            .switchIfEmpty(Mono.just(keycloakData));
                });
    }
}
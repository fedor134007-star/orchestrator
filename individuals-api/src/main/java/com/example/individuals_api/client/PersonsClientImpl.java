package com.example.individuals_api.client;

import com.example.individuals_api.exception.PersonServiceException;
import net.example.person.client.api.UsersApi;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UserResponse;
import net.generated.individuals.dto.RegistrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Supplier;

/**
 * Адаптер между портом {@link PersonsClient} и сгенерированным клиентом person-service.
 *
 * <p>Сгенерированный клиент синхронный (RestClient), поэтому вызовы выполняются на
 * {@code boundedElastic}: реактивный конвейер оркестратора не блокируется.</p>
 *
 * <p>404 транслируется в пустой {@link Mono} — для вызывающего кода «пользователя нет» это
 * не ошибка, а отсутствие значения. Остальные статусы превращаются в
 * {@link PersonServiceException} с сохранением кода ответа.</p>
 */
@Component
public class PersonsClientImpl implements PersonsClient {

    private static final Logger log = LoggerFactory.getLogger(PersonsClientImpl.class);

    private final UsersApi usersApi;

    public PersonsClientImpl(UsersApi usersApi) {
        this.usersApi = usersApi;
    }

    @Override
    public Mono<UserResponse> registerPerson(RegistrationRequest registrationRequest) {
        return blocking(() -> call(() -> usersApi.createUser(toCreateRequest(registrationRequest)), "createUser"));
    }

    @Override
    public Mono<UserResponse> getPersonByEmail(String email) {
        return blocking(() -> call(() -> usersApi.getUserByEmail(email), "getUserByEmail"))
                .onErrorResume(this::emptyIfNotFound);
    }

    private CreateUserRequest toCreateRequest(RegistrationRequest registrationRequest) {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail(registrationRequest.getEmail());
        request.setFirstName(registrationRequest.getFirstName());
        request.setLastName(registrationRequest.getLastName());
        return request;
    }

    private <T> Mono<T> blocking(Supplier<T> call) {
        return Mono.fromCallable(call::get).subscribeOn(Schedulers.boundedElastic());
    }

    private <T> T call(Supplier<ResponseEntity<T>> call, String operation) {
        try {
            ResponseEntity<T> response = call.get();
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            throw new PersonServiceException(
                    "person-service вернул " + response.getStatusCode() + " при вызове " + operation,
                    response.getStatusCode().value(),
                    null);
        } catch (RestClientResponseException ex) {
            log.warn("Вызов person-service {} завершился статусом {}: {}",
                    operation, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PersonServiceException(
                    "person-service вернул " + ex.getStatusCode() + " при вызове " + operation,
                    ex.getStatusCode().value(),
                    ex);
        }
    }

    private <T> Mono<T> emptyIfNotFound(Throwable error) {
        if (error instanceof PersonServiceException personServiceException && personServiceException.isNotFound()) {
            return Mono.empty();
        }
        return Mono.error(error);
    }
}

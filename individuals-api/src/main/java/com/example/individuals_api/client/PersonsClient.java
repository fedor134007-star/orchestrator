package com.example.individuals_api.client;

import net.example.person.dto.UserResponse;
import net.generated.individuals.dto.RegistrationRequest;
import reactor.core.publisher.Mono;

/**
 * Порт доступа к person-service.
 *
 * <p>Оркестратор зависит от этого интерфейса, а не от сгенерированного клиента:
 * транспорт изолирован в адаптере {@link PersonsClientImpl}, поэтому смена способа
 * вызова (HTTP Service Clients, WebClient, gRPC) не затрагивает бизнес-логику.</p>
 */
public interface PersonsClient {

    Mono<UserResponse> registerPerson(RegistrationRequest registrationRequest);

    /**
     * @return пользователь или пустой {@link Mono}, если в person-service его нет (404)
     */
    Mono<UserResponse> getPersonByEmail(String email);
}

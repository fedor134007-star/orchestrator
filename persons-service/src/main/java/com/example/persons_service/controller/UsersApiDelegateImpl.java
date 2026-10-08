package com.example.persons_service.controller;

import com.example.persons_service.service.PersonService;
import net.example.person.api.UsersApiDelegate;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Реализация сгенерированного делегата: HTTP-слой без бизнес-логики.
 *
 * <p>Маршрутизацию и разбор запроса берёт на себя сгенерированный
 * {@code UsersApiController}, контракт остаётся единственным источником истины
 * о путях, кодах ответа и DTO. Тело запроса приходит как {@code Mono<Dto>} —
 * так его отдаёт реактивная генерация.</p>
 */
@Component
public class UsersApiDelegateImpl implements UsersApiDelegate {

    private final PersonService personService;

    public UsersApiDelegateImpl(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public Mono<ResponseEntity<UserResponse>> createUser(Mono<CreateUserRequest> createUserRequest,
                                                         ServerWebExchange exchange) {
        return createUserRequest
                .flatMap(personService::createUser)
                .map(created -> ResponseEntity.created(location(exchange, created.getId())).body(created));
    }

    @Override
    public Mono<ResponseEntity<UserResponse>> getUserById(UUID id, ServerWebExchange exchange) {
        return personService.getUserById(id).map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<UserResponse>> getUserByEmail(String email, ServerWebExchange exchange) {
        return personService.getUserByEmail(email).map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<UserResponse>> updateUser(UUID id,
                                                         Mono<UpdateUserRequest> updateUserRequest,
                                                         ServerWebExchange exchange) {
        return updateUserRequest
                .flatMap(request -> personService.updateUser(id, request))
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<Void>> deleteUser(UUID id, ServerWebExchange exchange) {
        return personService.deleteUser(id).thenReturn(ResponseEntity.noContent().build());
    }

    /** Location созданного ресурса — от текущего запроса, без привязки к Servlet API. */
    private URI location(ServerWebExchange exchange, UUID id) {
        return UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}

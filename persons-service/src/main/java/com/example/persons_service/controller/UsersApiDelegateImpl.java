package com.example.persons_service.controller;

import com.example.persons_service.exception.RequestValidationException;
import com.example.persons_service.service.PersonService;
import net.example.person.api.UsersApiDelegate;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

/**
 * Реализация сгенерированного делегата: HTTP-слой без бизнес-логики.
 *
 * <p>Маршрутизацию и разбор запроса берёт на себя сгенерированный
 * {@code UsersApiController} (Spring MVC, Servlet-стек), контракт остаётся единственным
 * источником истины о путях, кодах ответа и DTO.</p>
 */
@Component
public class UsersApiDelegateImpl implements UsersApiDelegate {

    private final PersonService personService;

    public UsersApiDelegateImpl(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public ResponseEntity<UserResponse> createUser(CreateUserRequest createUserRequest) {
        UserResponse created = personService.createUser(createUserRequest);
        return ResponseEntity.created(location(created.getId())).body(created);
    }

    @Override
    public ResponseEntity<UserResponse> getUserById(UUID id) {
        return ResponseEntity.ok(personService.getUserById(id));
    }

    @Override
    public ResponseEntity<UserResponse> getUserByEmail(String email) {
        return ResponseEntity.ok(personService.getUserByEmail(email));
    }

    @Override
    public ResponseEntity<UserResponse> updateUser(UUID id, UpdateUserRequest updateUserRequest) {
        return ResponseEntity.ok(personService.updateUser(id, updateUserRequest));
    }

    @Override
    public ResponseEntity<Void> deleteUser(UUID id, String mode) {
        personService.deleteUser(id, parseDeleteMode(mode));
        return ResponseEntity.noContent().build();
    }

    /**
     * Строгая проверка режима удаления до вызова сервиса.
     *
     * <p>Допустимы только {@code hard} и {@code soft} (значения enum из контракта).
     * Раньше любое иное значение молча трактовалось как физическое удаление — опечатка
     * в параметре приводила к необратимой потере данных. Теперь неизвестный режим
     * отклоняется с {@code 400}, и данные не меняются.</p>
     */
    private boolean parseDeleteMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return false; // контракт: по умолчанию hard
        }
        if ("soft".equals(mode)) {
            return true;
        }
        if ("hard".equals(mode)) {
            return false;
        }
        throw new RequestValidationException(Map.of(
                "mode", "Недопустимый режим удаления: ожидается soft или hard"));
    }

    /** Location созданного ресурса — от текущего запроса. */
    private URI location(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}

package com.example.persons_service.controller;

import com.example.persons_service.service.PersonService;
import net.example.person.api.UsersApiDelegate;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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
        personService.deleteUser(id, isSoft(mode));
        return ResponseEntity.noContent().build();
    }

    private boolean isSoft(String mode) {
        return "soft".equalsIgnoreCase(mode);
    }

    /** Location созданного ресурса — от текущего запроса. */
    private URI location(UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}

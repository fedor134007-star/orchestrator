package com.example.persons_service.controller;

import com.example.persons_service.service.PersonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.generated.person.api.PersonsApi;
import net.generated.person.dto.PersonRegistrationRequest;
import net.generated.person.dto.PersonResponse;
import net.generated.person.dto.PersonStatusUpdateRequest;
import net.generated.person.dto.PersonUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PersonController implements PersonsApi {

    private final PersonService personService;

    @Override
    public Mono<ResponseEntity<PersonResponse>> registerPerson(
            Mono<PersonRegistrationRequest> personRegistrationRequest,
            ServerWebExchange exchange) {

        return personRegistrationRequest
                .flatMap(personService::registerPerson)
                .map(response -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response));
    }

    @Override
    public Mono<ResponseEntity<PersonResponse>> getPersonByUid(
            UUID userUid,
            ServerWebExchange exchange) {

        return personService.getPersonByUid(userUid)
                .map(response -> ResponseEntity.ok(response));
    }

    @Override
    public Mono<ResponseEntity<PersonResponse>> getPersonByEmail(
            String email,
            ServerWebExchange exchange) {

        return personService.getPersonByEmail(email)
                .map(response -> ResponseEntity.ok(response));
    }

    @Override
    public Mono<ResponseEntity<PersonResponse>> updatePerson(
            UUID userUid,
            Mono<PersonUpdateRequest> personUpdateRequest,
            ServerWebExchange exchange) {

        return personUpdateRequest
                .flatMap(request -> personService.updatePerson(userUid, request))
                .map(response -> ResponseEntity.ok(response));
    }

    @Override
    public Mono<ResponseEntity<PersonResponse>> updatePersonStatus(
            UUID userUid,
            Mono<PersonStatusUpdateRequest> personStatusUpdateRequest,
            ServerWebExchange exchange) {

        return personStatusUpdateRequest
                .flatMap(request -> personService.updatePersonStatus(userUid, request))
                .map(response -> ResponseEntity.ok(response));
    }
}
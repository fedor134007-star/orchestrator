package com.example.persons_service.service;

import net.generated.person.dto.PersonRegistrationRequest;
import net.generated.person.dto.PersonResponse;
import net.generated.person.dto.PersonStatusUpdateRequest;
import net.generated.person.dto.PersonUpdateRequest;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PersonService {

    Mono<PersonResponse> registerPerson(PersonRegistrationRequest request);

    Mono<PersonResponse> getPersonByUid(UUID userUid);

    Mono<PersonResponse> getPersonByEmail(String email);

    Mono<PersonResponse> updatePerson(UUID userUid, PersonUpdateRequest request);

    Mono<PersonResponse> updatePersonStatus(UUID userUid, PersonStatusUpdateRequest request);
}
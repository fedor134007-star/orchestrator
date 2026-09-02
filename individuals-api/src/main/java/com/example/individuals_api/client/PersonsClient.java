package com.example.individuals_api.client;

import net.generated.individuals.dto.RegistrationRequest;
import net.generated.person.dto.PersonResponse;
import reactor.core.publisher.Mono;

public interface PersonsClient {


    Mono<PersonResponse> registerPerson(RegistrationRequest registrationRequest);


    Mono<PersonResponse> getPersonByEmail(String email);


    Mono<PersonResponse> getPersonByUid(String userUid);
}
package com.example.individuals_api.client;

import com.example.individuals_api.config.AdminTokenProvider;
import com.example.individuals_api.exception.PersonServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.generated.individuals.dto.RegistrationRequest;
import net.generated.person.dto.PersonRegistrationRequest;
import net.generated.person.dto.PersonResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersonsClientImpl implements PersonsClient {

    @Qualifier("personServiceWebClient")
    private final WebClient personServiceWebClient;
    private final AdminTokenProvider tokenProvider;

    @Override
    public Mono<PersonResponse> registerPerson(RegistrationRequest registrationRequest) {
        log.debug("Registering person in person-service for email: {}", registrationRequest.getEmail());

        PersonRegistrationRequest personRequest = new PersonRegistrationRequest();
        personRequest.setEmail(registrationRequest.getEmail());
        personRequest.setFirstName(registrationRequest.getFirstName());
        personRequest.setLastName(registrationRequest.getLastName());

        return tokenProvider.getToken()
                .flatMap(token ->
                        personServiceWebClient.post()
                                .uri("/api/v1/persons/registration")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(personRequest)
                                .retrieve()
                                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                        response -> response.bodyToMono(String.class)
                                                .flatMap(errorBody -> {
                                                    log.error("Error from person-service: {}", errorBody);
                                                    return Mono.error(new PersonServiceException(
                                                            "Person service error: " + errorBody));
                                                })
                                )
                                .bodyToMono(PersonResponse.class)
                                .doOnSuccess(response ->
                                        log.info("Person registered with userUid: {}", response.getUserUid()))
                );
    }

    @Override
    public Mono<PersonResponse> getPersonByEmail(String email) {
        log.debug("Getting person by email: {}", email);

        return tokenProvider.getToken()
                .flatMap(token ->
                        personServiceWebClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/api/v1/persons/by-email")
                                        .queryParam("email", email)
                                        .build())
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                        response -> response.bodyToMono(String.class)
                                                .flatMap(errorBody -> {
                                                    log.error("Error from person-service: {}", errorBody);
                                                    return Mono.error(new PersonServiceException(
                                                            "Person service error: " + errorBody));
                                                })
                                )
                                .bodyToMono(PersonResponse.class)
                );
    }

    @Override
    public Mono<PersonResponse> getPersonByUid(String userUid) {
        log.debug("Getting person by userUid: {}", userUid);

        return tokenProvider.getToken()
                .flatMap(token ->
                        personServiceWebClient.get()
                                .uri("/api/v1/persons/{userUid}", userUid)
                                .header("Authorization", "Bearer " + token)
                                .retrieve()
                                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                        response -> response.bodyToMono(String.class)
                                                .flatMap(errorBody -> {
                                                    log.error("Error from person-service: {}", errorBody);
                                                    return Mono.error(new PersonServiceException(
                                                            "Person service error: " + errorBody));
                                                })
                                )
                                .bodyToMono(PersonResponse.class)
                );
    }
}
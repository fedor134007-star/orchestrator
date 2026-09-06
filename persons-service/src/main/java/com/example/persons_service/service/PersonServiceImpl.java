package com.example.persons_service.service;

import com.example.persons_service.entity.Person;
import com.example.persons_service.entity.PersonStatus;
import com.example.persons_service.exception.PersonAlreadyExistsException;
import com.example.persons_service.exception.PersonNotFoundException;
import com.example.persons_service.mapper.PersonMapper;
import com.example.persons_service.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.generated.person.dto.PersonRegistrationRequest;
import net.generated.person.dto.PersonResponse;
import net.generated.person.dto.PersonStatusUpdateRequest;
import net.generated.person.dto.PersonUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonServiceImpl implements PersonService {

    private final PersonRepository personRepository;
    private final PersonMapper personMapper;

    @Override
    @Transactional
    public Mono<PersonResponse> registerPerson(PersonRegistrationRequest request) {
        log.debug("Registering person with email: {}", request.getEmail());

        return personRepository.existsByEmail(request.getEmail())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new PersonAlreadyExistsException(
                                "Person with email " + request.getEmail() + " already exists"));
                    }

                    Person person = personMapper.toEntity(request);

                    return personRepository.save(person)
                            .doOnSuccess(savedPerson ->
                                    log.info("Person created with userUid: {}", savedPerson.getUserUid()))
                            .map(personMapper::toResponse);
                });
    }

    @Override
    public Mono<PersonResponse> getPersonByUid(UUID userUid) {
        log.debug("Getting person by userUid: {}", userUid);

        return personRepository.findById(userUid)
                .switchIfEmpty(Mono.error(new PersonNotFoundException(
                        "Person with userUid " + userUid + " not found")))
                .map(personMapper::toResponse);
    }

    @Override
    public Mono<PersonResponse> getPersonByEmail(String email) {
        log.debug("Getting person by email: {}", email);

        return personRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new PersonNotFoundException(
                        "Person with email " + email + " not found")))
                .map(personMapper::toResponse);
    }

    @Override
    @Transactional
    public Mono<PersonResponse> updatePerson(UUID userUid, PersonUpdateRequest request) {
        log.debug("Updating person with userUid: {}", userUid);

        return personRepository.findById(userUid)
                .switchIfEmpty(Mono.error(new PersonNotFoundException(
                        "Person with userUid " + userUid + " not found")))
                .flatMap(existingPerson -> {
                    personMapper.updateEntityFromRequest(request, existingPerson);
                    return personRepository.save(existingPerson)
                            .doOnSuccess(updatedPerson ->
                                    log.info("Person updated with userUid: {}", updatedPerson.getUserUid()))
                            .map(personMapper::toResponse);
                });
    }


    @Override
    @Transactional
    public Mono<PersonResponse> updatePersonStatus(UUID userUid, PersonStatusUpdateRequest request) {
        log.debug("Updating status for person with userUid: {} to {}", userUid, request.getStatus());

        return personRepository.findById(userUid)
                .switchIfEmpty(Mono.error(new PersonNotFoundException(
                        "Person with userUid " + userUid + " not found")))
                .flatMap(existingPerson -> {
                    PersonStatus newStatus = PersonStatus.valueOf(request.getStatus().getValue());
                    existingPerson.setStatus(newStatus);

                    return personRepository.save(existingPerson)
                            .doOnSuccess(updatedPerson ->
                                    log.info("Person status updated for userUid: {} to {}",
                                            updatedPerson.getUserUid(), updatedPerson.getStatus()))
                            .map(personMapper::toResponse);
                });
    }
}
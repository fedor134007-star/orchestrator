package com.example.persons_service.repository;

import com.example.persons_service.entity.Person;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface PersonRepository extends ReactiveCrudRepository<Person, UUID> {

    Mono<Person> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);

    @Query("SELECT * FROM persons WHERE LOWER(email) = LOWER(:email)")
    Mono<Person> findByEmailIgnoreCase(String email);
}
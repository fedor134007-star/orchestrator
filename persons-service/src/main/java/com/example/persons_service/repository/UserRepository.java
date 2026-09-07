package com.example.persons_service.repository;

import com.example.persons_service.entity.UserEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Поиск по email регистронезависимый: {@code lower(email)} совпадает с уникальным
 * функциональным индексом {@code uk_users_email_lower}, поэтому база использует индекс.
 */
public interface UserRepository extends ReactiveCrudRepository<UserEntity, UUID> {

    @Query("SELECT * FROM person.users WHERE lower(email) = lower(:email)")
    Mono<UserEntity> findByEmailIgnoreCase(@Param("email") String email);

    @Query("SELECT EXISTS(SELECT 1 FROM person.users WHERE lower(email) = lower(:email))")
    Mono<Boolean> existsByEmailIgnoreCase(@Param("email") String email);
}

package com.example.persons_service.repository;

import com.example.persons_service.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Поиск по email регистронезависимый: {@code lower(email)} совпадает с уникальным
 * функциональным индексом {@code uk_users_email_lower}, поэтому база использует индекс.
 * Мягко удалённые пользователи (deleted_at не null) исключаются из поиска и проверки
 * уникальности — email освобождается для повторной регистрации.
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @Query(value = "SELECT * FROM person.users WHERE lower(email) = lower(:email) AND deleted_at IS NULL",
            nativeQuery = true)
    Optional<UserEntity> findByEmailIgnoreCase(@Param("email") String email);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM person.users WHERE lower(email) = lower(:email) AND deleted_at IS NULL)",
            nativeQuery = true)
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}

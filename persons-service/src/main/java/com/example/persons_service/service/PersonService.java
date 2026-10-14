package com.example.persons_service.service;

import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;

import java.util.UUID;

public interface PersonService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUserById(UUID id);

    UserResponse getUserByEmail(String email);

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    /**
     * Удаляет пользователя.
     *
     * @param soft {@code true} — мягкое удаление (компенсационное: строка остаётся в БД
     *             и в аудите, но исключается из чтений); {@code false} — физическое
     */
    void deleteUser(UUID id, boolean soft);
}

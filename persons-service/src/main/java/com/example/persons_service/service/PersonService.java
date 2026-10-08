package com.example.persons_service.service;

import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PersonService {

    Mono<UserResponse> createUser(CreateUserRequest request);

    Mono<UserResponse> getUserById(UUID id);

    Mono<UserResponse> getUserByEmail(String email);

    Mono<UserResponse> updateUser(UUID id, UpdateUserRequest request);

    Mono<Void> deleteUser(UUID id);
}

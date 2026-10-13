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

    void deleteUser(UUID id);
}

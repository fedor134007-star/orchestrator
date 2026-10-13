package com.example.persons_service.exception;

import java.util.UUID;

/** Пользователь с указанным идентификатором не найден → 404. */
public class UserNotFoundException extends RuntimeException {

    private final UUID userId;

    public UserNotFoundException(UUID userId) {
        super("Пользователь с идентификатором " + userId + " не найден");
        this.userId = userId;
    }

    public UserNotFoundException(String email) {
        super("Пользователь с email " + email + " не найден");
        this.userId = null;
    }

    public UUID getUserId() {
        return userId;
    }
}

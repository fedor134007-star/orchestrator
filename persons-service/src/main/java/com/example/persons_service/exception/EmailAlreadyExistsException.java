package com.example.persons_service.exception;

/** Email уже занят другим пользователем → 409. */
public class EmailAlreadyExistsException extends RuntimeException {

    private final String email;

    public EmailAlreadyExistsException(String email) {
        super("Пользователь с таким email уже существует");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}

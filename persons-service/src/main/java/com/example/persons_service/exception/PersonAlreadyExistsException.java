package com.example.persons_service.exception;

public class PersonAlreadyExistsException extends RuntimeException {
    public PersonAlreadyExistsException(String message) {
        super(message);
    }
}
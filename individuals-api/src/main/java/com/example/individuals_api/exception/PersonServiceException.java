package com.example.individuals_api.exception;

public class PersonServiceException extends RuntimeException {

    public PersonServiceException(String message) {
        super(message);
    }

    public PersonServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
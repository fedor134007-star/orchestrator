package com.example.individuals_api.exception;

public class PartialRegistrationException extends RuntimeException {

    private final String code = "PARTIAL_REGISTRATION";

    public PartialRegistrationException(String message) {
        super(message);
    }

    public PartialRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getCode() {
        return code;
    }
}
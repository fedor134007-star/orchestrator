package com.example.individuals_api.exception;

/**
 * Сбой при обращении к person-service.
 * Хранит HTTP-статус ответа, чтобы вызывающий код мог отличить «не найдено» от ошибки.
 */
public class PersonServiceException extends RuntimeException {

    private final int status;

    public PersonServiceException(String message) {
        this(message, 0, null);
    }

    public PersonServiceException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public PersonServiceException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }
}

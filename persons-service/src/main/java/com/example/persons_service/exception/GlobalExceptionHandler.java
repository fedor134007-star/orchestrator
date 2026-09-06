package com.example.persons_service.exception;

import lombok.extern.slf4j.Slf4j;
import net.generated.person.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PersonNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handlePersonNotFound(
            PersonNotFoundException ex, ServerWebExchange exchange) {

        log.warn("Person not found: {}", ex.getMessage());

        ErrorResponse error = createErrorResponse(
                exchange.getRequest().getPath().value(),
                HttpStatus.NOT_FOUND,
                "Person Not Found",
                ex.getMessage()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(error));
    }

    @ExceptionHandler(PersonAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handlePersonAlreadyExists(
            PersonAlreadyExistsException ex, ServerWebExchange exchange) {

        log.warn("Person already exists: {}", ex.getMessage());

        ErrorResponse error = createErrorResponse(
                exchange.getRequest().getPath().value(),
                HttpStatus.CONFLICT,
                "Person Already Exists",
                ex.getMessage()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(error));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {

        log.error("Unexpected error occurred", ex);

        ErrorResponse error = createErrorResponse(
                exchange.getRequest().getPath().value(),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred"
        );

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
    }

    private ErrorResponse createErrorResponse(
            String path, HttpStatus status, String error, String message) {

        ErrorResponse response = new ErrorResponse();
        response.setTimestamp(OffsetDateTime.now());
        response.setPath(path);
        response.setStatus(status.value());
        response.setError(error);
        response.setMessage(message);
        // Здесь можно добавить traceId из MDC

        return response;
    }
}
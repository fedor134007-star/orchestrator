package com.example.individuals_api.exception;

import lombok.extern.slf4j.Slf4j;
import net.generated.individualls.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebClientException(
            WebClientResponseException ex, ServerWebExchange exchange) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        String error = status.getReasonPhrase();
        String message = switch (ex.getStatusCode().value()) {
            case 409 -> "Пользователь с таким email уже существует";
            case 400 -> "Неверные данные запроса";
            case 401 -> "Ошибка авторизации";
            case 403 -> "Доступ запрещен";
            default -> "Внутренняя ошибка сервера";
        };

        log.error("Keycloak error: {} {}", ex.getStatusCode(), message);

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        errorResponse.setPath(exchange.getRequest().getPath().value());
        errorResponse.setStatus(status.value());
        errorResponse.setError(error);
        errorResponse.setMessage(message);
        errorResponse.setTraceId(UUID.randomUUID().toString());

        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(
            IllegalArgumentException ex, ServerWebExchange exchange) {

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        errorResponse.setPath(exchange.getRequest().getPath().value());
        errorResponse.setStatus(400);
        errorResponse.setError("Bad Request");
        errorResponse.setMessage(ex.getMessage());
        errorResponse.setTraceId(UUID.randomUUID().toString());

        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {

        log.error("Unexpected error", ex);

        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        errorResponse.setPath(exchange.getRequest().getPath().value());
        errorResponse.setStatus(500);
        errorResponse.setError("Internal Server Error");
        errorResponse.setMessage("Внутренняя ошибка сервера");
        errorResponse.setTraceId(UUID.randomUUID().toString());

        return Mono.just(ResponseEntity.status(500).body(errorResponse));
    }
}
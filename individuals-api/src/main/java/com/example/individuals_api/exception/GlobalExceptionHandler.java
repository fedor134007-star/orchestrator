package com.example.individuals_api.exception;

import lombok.extern.slf4j.Slf4j;
import net.generated.individuals.dto.ErrorResponse;
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
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        log.error("Keycloak error: {} {}", ex.getStatusCode(), messageFor(status));
        return Mono.just(error(status, messageFor(status), exchange));
    }

    /**
     * Сбой при обращении к person-service.
     *
     * <p>Раньше вызовы шли через WebClient и падали как {@code WebClientResponseException};
     * теперь клиент — HTTP Service Client и бросает {@link PersonServiceException}.
     * Статус ответа сохраняется, иначе клиент вместо 409/404 получал бы 500.
     * Если ответа нет вовсе (сервис недоступен) — 502 Bad Gateway.</p>
     */
    @ExceptionHandler(PersonServiceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handlePersonServiceException(
            PersonServiceException ex, ServerWebExchange exchange) {

        HttpStatus status = ex.getStatus() > 0 ? HttpStatus.resolve(ex.getStatus()) : null;
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        log.error("person-service error: {}", ex.getMessage());
        return Mono.just(error(status, messageFor(status), exchange));
    }

    /**
     * Ошибка сценария регистрации: причина разворачивается, чтобы не потерять статус
     * соседнего сервиса (например, 409 «email уже занят» от person-service или Keycloak).
     */
    @ExceptionHandler(RegistrationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRegistrationException(
            RegistrationException ex, ServerWebExchange exchange) {

        Throwable cause = ex.getCause();
        if (cause instanceof PersonServiceException personServiceException) {
            return handlePersonServiceException(personServiceException, exchange);
        }
        if (cause instanceof WebClientResponseException webClientResponseException) {
            return handleWebClientException(webClientResponseException, exchange);
        }
        if (cause instanceof PasswordMismatchException) {
            return handlePasswordMismatch((PasswordMismatchException) cause, exchange);
        }
        log.error("Registration failed", ex);
        return Mono.just(error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", exchange));
    }

    @ExceptionHandler(PasswordMismatchException.class)
    public Mono<ResponseEntity<ErrorResponse>> handlePasswordMismatch(
            PasswordMismatchException ex, ServerWebExchange exchange) {

        return Mono.just(error(HttpStatus.BAD_REQUEST, ex.getMessage(), exchange));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(
            IllegalArgumentException ex, ServerWebExchange exchange) {

        return Mono.just(error(HttpStatus.BAD_REQUEST, ex.getMessage(), exchange));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex, ServerWebExchange exchange) {

        log.error("Unexpected error", ex);
        return Mono.just(error(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера", exchange));
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, ServerWebExchange exchange) {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        errorResponse.setPath(exchange.getRequest().getPath().value());
        errorResponse.setStatus(status.value());
        errorResponse.setError(status.getReasonPhrase());
        errorResponse.setMessage(message);
        errorResponse.setTraceId(UUID.randomUUID().toString());
        return ResponseEntity.status(status).body(errorResponse);
    }

    private String messageFor(HttpStatus status) {
        return switch (status.value()) {
            case 409 -> "Пользователь с таким email уже существует";
            case 400 -> "Неверные данные запроса";
            case 401 -> "Ошибка авторизации";
            case 403 -> "Доступ запрещен";
            case 404 -> "Пользователь не найден";
            case 502, 503 -> "Сервис пользовательских данных временно недоступен";
            default -> "Внутренняя ошибка сервера";
        };
    }
}

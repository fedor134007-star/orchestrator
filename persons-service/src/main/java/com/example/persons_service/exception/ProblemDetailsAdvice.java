package com.example.persons_service.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Единый слой ошибок: ответы в формате RFC 9457 (application/problem+json).
 *
 * <p>Контракт ответа об ошибке — {@code ProblemResponse} из OpenAPI, поля
 * {@code type/title/status/detail/instance} плюс расширение {@code errors}
 * с детализацией по полям.</p>
 *
 * <p>Ошибки, поднятые вне обработчика (например, запрос к несуществующему пути),
 * до этого advice не доходят — их приводит к тому же формату
 * {@link ProblemDetailsErrorWebExceptionHandler}.</p>
 */
@RestControllerAdvice
public class ProblemDetailsAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(RequestValidationException.class)
    ProblemDetail handleRequestValidation(RequestValidationException ex, ServerWebExchange exchange) {
        ProblemDetail problem = validationProblem(exchange, "Запрос не прошёл валидацию");
        problem.setProperty("errors", ex.getErrors());
        log.warn("Валидация запроса не пройдена: {}", ex.getErrors());
        return problem;
    }

    /** Ошибки привязки и валидации тела и query-параметров в WebFlux. */
    @ExceptionHandler(WebExchangeBindException.class)
    ProblemDetail handleBinding(WebExchangeBindException ex, ServerWebExchange exchange) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = validationProblem(exchange, "Запрос не прошёл валидацию");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Валидация параметров метода (аннотации на сгенерированном интерфейсе). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex, ServerWebExchange exchange) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));

        ProblemDetail problem = validationProblem(exchange, "Параметры запроса не прошли валидацию");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Битый JSON в теле запроса или неконвертируемый параметр (например, не UUID). */
    @ExceptionHandler({ServerWebInputException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail handleMalformedRequest(Exception ex, ServerWebExchange exchange) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.VALIDATION_ERROR,
                "Ошибка формата запроса",
                "Тело запроса или параметр пути имеют неверный формат",
                exchange);
    }

    @ExceptionHandler(UnknownCountryException.class)
    ProblemDetail handleUnknownCountry(UnknownCountryException ex, ServerWebExchange exchange) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.UNKNOWN_COUNTRY,
                "Неизвестная страна",
                ex.getMessage(),
                exchange);
    }

    @ExceptionHandler(IncompleteAggregateUpdateException.class)
    ProblemDetail handleIncompleteAggregate(IncompleteAggregateUpdateException ex, ServerWebExchange exchange) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.VALIDATION_ERROR,
                "Неполное обновление агрегата",
                ex.getMessage(),
                exchange);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex, ServerWebExchange exchange) {
        return problem(
                HttpStatus.NOT_FOUND,
                ProblemTypes.USER_NOT_FOUND,
                "Пользователь не найден",
                ex.getMessage(),
                exchange);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ProblemDetail handleEmailConflict(EmailAlreadyExistsException ex, ServerWebExchange exchange) {
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.EMAIL_ALREADY_EXISTS,
                "Конфликт данных",
                ex.getMessage(),
                exchange);
    }

    /**
     * Страховка от гонки: два одновременных создания с одним email проходят проверку
     * в приложении, но уникальный индекс БД пропускает только одну запись.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, ServerWebExchange exchange) {
        log.warn("Нарушение целостности данных: {}", ex.getMostSpecificCause().getMessage());
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.EMAIL_ALREADY_EXISTS,
                "Конфликт данных",
                "Запись с такими данными уже существует",
                exchange);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex, ServerWebExchange exchange) {
        log.warn("Конкурентное изменение агрегата: {}", ex.getMessage());
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.CONCURRENT_MODIFICATION,
                "Конкурентное изменение",
                "Агрегат был изменён параллельно, повторите запрос",
                exchange);
    }

    /** Прочие статусные исключения WebFlux. */
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        boolean notFound = status == HttpStatus.NOT_FOUND;
        return problem(
                status,
                notFound ? ProblemTypes.RESOURCE_NOT_FOUND : ProblemTypes.INTERNAL_ERROR,
                notFound ? "Ресурс не найден" : "Ошибка обработки запроса",
                notFound ? "Запрошенный ресурс не существует" : status.getReasonPhrase(),
                exchange);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, ServerWebExchange exchange) {
        log.error("Непредвиденная ошибка при обработке запроса {}", exchange.getRequest().getPath(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL_ERROR,
                "Внутренняя ошибка",
                "Непредвиденная ошибка сервиса",
                exchange);
    }

    private ProblemDetail validationProblem(ServerWebExchange exchange, String detail) {
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, "Ошибка валидации", detail, exchange);
    }

    private ProblemDetail problem(HttpStatus status,
                                  URI type,
                                  String title,
                                  String detail,
                                  ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        return problem;
    }
}

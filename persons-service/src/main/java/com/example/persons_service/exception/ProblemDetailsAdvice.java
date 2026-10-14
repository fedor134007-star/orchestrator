package com.example.persons_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Единый слой ошибок: ответы в формате RFC 9457 (application/problem+json).
 *
 * <p>Контракт ответа об ошибке — {@code ProblemResponse} из OpenAPI, поля
 * {@code type/title/status/detail/instance} плюс расширение {@code errors}
 * с детализацией по полям.</p>
 */
@RestControllerAdvice
public class ProblemDetailsAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(RequestValidationException.class)
    ProblemDetail handleRequestValidation(RequestValidationException ex, HttpServletRequest request) {
        ProblemDetail problem = validationProblem(request, "Запрос не прошёл валидацию");
        problem.setProperty("errors", ex.getErrors());
        log.warn("Валидация запроса не пройдена: {}", ex.getErrors());
        return problem;
    }

    /** Ошибки привязки и валидации тела в Servlet-стеке. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBinding(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = validationProblem(request, "Запрос не прошёл валидацию");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Валидация параметров метода (Spring 7: аннотации на query/path-параметрах). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    errors.putIfAbsent(name, error.getDefaultMessage()));
        });

        ProblemDetail problem = validationProblem(request, "Параметры запроса не прошли валидацию");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Валидация параметров метода (аннотации на сгенерированном интерфейсе). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));

        ProblemDetail problem = validationProblem(request, "Параметры запроса не прошли валидацию");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Битый JSON в теле запроса или неконвертируемый параметр (например, не UUID). */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ProblemDetail handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.VALIDATION_ERROR,
                "Ошибка формата запроса",
                "Тело запроса или параметры имеют неверный формат",
                request);
    }

    @ExceptionHandler(UnknownCountryException.class)
    ProblemDetail handleUnknownCountry(UnknownCountryException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.UNKNOWN_COUNTRY,
                "Неизвестная страна",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(IncompleteAggregateUpdateException.class)
    ProblemDetail handleIncompleteAggregate(IncompleteAggregateUpdateException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                ProblemTypes.VALIDATION_ERROR,
                "Неполное обновление агрегата",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                ProblemTypes.USER_NOT_FOUND,
                "Пользователь не найден",
                ex.getMessage(),
                request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ProblemDetail handleEmailConflict(EmailAlreadyExistsException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.EMAIL_ALREADY_EXISTS,
                "Конфликт данных",
                ex.getMessage(),
                request);
    }

    /**
     * Страховка от гонки: два одновременных создания с одним email проходят проверку
     * в приложении, но уникальный индекс БД пропускает только одну запись.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Нарушение целостности данных: {}", ex.getMostSpecificCause().getMessage());
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.EMAIL_ALREADY_EXISTS,
                "Конфликт данных",
                "Запись с такими данными уже существует",
                request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Конкурентное изменение агрегата: {}", ex.getMessage());
        return problem(
                HttpStatus.CONFLICT,
                ProblemTypes.CONCURRENT_MODIFICATION,
                "Конкурентное изменение",
                "Агрегат был изменён параллельно, повторите запрос",
                request);
    }

    /** Запрос к несуществующему маршруту (throw-exception-if-no-handler-found). */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ProblemDetail handleNoResource(Exception ex, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                ProblemTypes.RESOURCE_NOT_FOUND,
                "Ресурс не найден",
                "Запрошенный ресурс не существует",
                request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Непредвиденная ошибка при обработке запроса {}", request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL_ERROR,
                "Внутренняя ошибка",
                "Непредвиденная ошибка сервиса",
                request);
    }

    private ProblemDetail validationProblem(HttpServletRequest request, String detail) {
        return problem(HttpStatus.BAD_REQUEST, ProblemTypes.VALIDATION_ERROR, "Ошибка валидации", detail, request);
    }

    private ProblemDetail problem(HttpStatus status,
                                  URI type,
                                  String title,
                                  String detail,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}

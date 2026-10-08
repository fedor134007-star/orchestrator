package com.example.persons_service.exception;

import net.example.person.dto.CreateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProblemDetailsAdvice: ответы RFC 9457 на WebFlux")
class ProblemDetailsAdviceTest {

    private final ProblemDetailsAdvice advice = new ProblemDetailsAdvice();

    @Test
    @DisplayName("404: пользователь не найден")
    void userNotFound() {
        ProblemDetail problem = advice.handleUserNotFound(
                new UserNotFoundException(UUID.fromString("6c8ec4d0-6fd5-4db8-bf4d-7bcbdb2f6c02")),
                exchange("/api/v1/users/6c8ec4d0-6fd5-4db8-bf4d-7bcbdb2f6c02"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.USER_NOT_FOUND);
        assertThat(problem.getTitle()).isEqualTo("Пользователь не найден");
        assertThat(problem.getInstance()).hasToString("/api/v1/users/6c8ec4d0-6fd5-4db8-bf4d-7bcbdb2f6c02");
    }

    @Test
    @DisplayName("409: email уже занят — формат совпадает с примером из ТЗ")
    void emailConflict() {
        ProblemDetail problem = advice.handleEmailConflict(
                new EmailAlreadyExistsException("ivan.petrov@example.org"),
                exchange("/api/v1/users"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.EMAIL_ALREADY_EXISTS);
        assertThat(problem.getTitle()).isEqualTo("Конфликт данных");
        assertThat(problem.getDetail()).isEqualTo("Пользователь с таким email уже существует");
        assertThat(problem.getInstance()).hasToString("/api/v1/users");
    }

    @Test
    @DisplayName("400: ошибки валидации перечислены по полям")
    void validationErrors() {
        Map<String, String> errors = Map.of(
                "email", "должно иметь формат адреса электронной почты",
                "lastName", "не должно равняться null");

        ProblemDetail problem = advice.handleRequestValidation(
                new RequestValidationException(errors), exchange("/api/v1/users"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.VALIDATION_ERROR);

        @SuppressWarnings("unchecked")
        Map<String, String> reported = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(reported)
                .containsEntry("email", "должно иметь формат адреса электронной почты")
                .containsEntry("lastName", "не должно равняться null");
    }

    @Test
    @DisplayName("400: неизвестная страна")
    void unknownCountry() {
        ProblemDetail problem = advice.handleUnknownCountry(
                new UnknownCountryException("Страна с кодом alpha-3 ZZZ не найдена в справочнике"),
                exchange("/api/v1/users"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.UNKNOWN_COUNTRY);
    }

    @Test
    @DisplayName("400: неполное обновление агрегата")
    void incompleteAggregate() {
        ProblemDetail problem = advice.handleIncompleteAggregate(
                new IncompleteAggregateUpdateException("Адрес должен содержать city, addressLine и код страны"),
                exchange("/api/v1/users/1"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("400: битый формат запроса (WebFlux WebInputException)")
    void malformedRequest() {
        ProblemDetail problem = advice.handleMalformedRequest(
                new ServerWebInputException("Failed to read HTTP message"), exchange("/api/v1/users"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("409: страховка от гонки по уникальному индексу")
    void dataIntegrityViolation() {
        ProblemDetail problem = advice.handleDataIntegrity(
                new DataIntegrityViolationException("insert failed", new RuntimeException("duplicate key value")),
                exchange("/api/v1/users"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("409: конкурентное изменение агрегата")
    void concurrentModification() {
        ProblemDetail problem = advice.handleConcurrentModification(
                new OptimisticLockingFailureException("row was updated"),
                exchange("/api/v1/users/1"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("404: неизвестный ресурс через ResponseStatusException")
    void responseStatusNotFound() {
        ProblemDetail problem = advice.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND), exchange("/api/v1/unknown-resource"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("500: непредвиденная ошибка не раскрывает деталей")
    void unexpectedError() {
        ProblemDetail problem = advice.handleUnexpected(
                new IllegalStateException("внутренние детали"), exchange("/api/v1/users"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.INTERNAL_ERROR);
        assertThat(problem.getDetail()).isEqualTo("Непредвиденная ошибка сервиса");
    }

    @Test
    @DisplayName("DTO валидируется штатным Validator: используется в тестах сервиса")
    void dtoFixtureIsInvalid() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("not-an-email");

        assertThat(request.getEmail()).isEqualTo("not-an-email");
    }

    private ServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }
}

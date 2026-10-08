package com.example.persons_service.service;

import com.example.persons_service.exception.RequestValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Валидация тела запроса по аннотациям контракта.
 *
 * <p>В WebFlux тело приходит как {@code Mono<Dto>} — автоматическая проверка тела ведёт себя
 * иначе, чем в Servlet-стеке, поэтому валидация выполняется явно: результат одинаков
 * независимо от стека и от способа вызова.</p>
 *
 * <p>Перед проверкой email нормализуется: случайные пробелы при копировании не должны
 * превращаться в 400, тем более что уникальность email в БД и так регистронезависимая.</p>
 */
@Component
public class RequestValidator {

    private final Validator validator;

    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    public Mono<CreateUserRequest> validateCreate(CreateUserRequest request) {
        return validateAfterTrimmingEmail(request, request.getEmail(), request::setEmail);
    }

    public Mono<UpdateUserRequest> validateUpdate(UpdateUserRequest request) {
        return validateAfterTrimmingEmail(request, request.getEmail(), request::setEmail);
    }

    private <T> Mono<T> validateAfterTrimmingEmail(T request, String email, Consumer<String> emailSetter) {
        if (email != null) {
            emailSetter.accept(email.trim());
        }
        return validate(request);
    }

    private <T> Mono<T> validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return Mono.just(request);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        violations.forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return Mono.error(new RequestValidationException(errors));
    }
}

package com.example.persons_service.service;

import com.example.persons_service.exception.RequestValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Валидация тела запроса по аннотациям контракта.
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

    public CreateUserRequest validateCreate(CreateUserRequest request) {
        if (request.getEmail() != null) {
            request.setEmail(request.getEmail().trim());
        }
        validate(request);
        return request;
    }

    public UpdateUserRequest validateUpdate(UpdateUserRequest request) {
        if (request.getEmail() != null) {
            request.setEmail(request.getEmail().trim());
        }
        validate(request);
        return request;
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (violations.isEmpty()) {
            return;
        }
        Map<String, String> errors = new LinkedHashMap<>();
        violations.forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        throw new RequestValidationException(errors);
    }
}

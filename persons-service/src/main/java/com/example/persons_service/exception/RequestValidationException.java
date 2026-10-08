package com.example.persons_service.exception;

import java.util.Map;

/**
 * Ошибка валидации тела запроса, найденная явной проверкой в сервисном слое.
 *
 * <p>В WebFlux {@code @Valid} на теле запроса работает не так очевидно, как в Servlet-стеке
 * (параметр имеет тип {@code Mono<Dto>}), поэтому валидация выполняется явно и всегда даёт
 * одинаковый ответ: 400 с детализацией по полям.</p>
 */
public class RequestValidationException extends RuntimeException {

    private final transient Map<String, String> errors;

    public RequestValidationException(Map<String, String> errors) {
        super("Запрос не прошёл валидацию");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}

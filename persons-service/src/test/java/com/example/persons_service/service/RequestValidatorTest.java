package com.example.persons_service.service;

import com.example.persons_service.exception.RequestValidationException;
import jakarta.validation.Validation;
import net.example.person.dto.CreateAddressRequest;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestValidator: проверка тела запроса и нормализация email")
class RequestValidatorTest {

    private final RequestValidator validator =
            new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    @DisplayName("корректный запрос проходит дальше без изменений")
    void acceptsValidCreateRequest() {
        CreateUserRequest request = validCreateRequest();

        StepVerifier.create(validator.validateCreate(request))
                .assertNext(valid -> assertThat(valid).isSameAs(request))
                .verifyComplete();
    }

    @Test
    @DisplayName("ошибки перечислены по именам полей")
    void reportsFieldErrors() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("not-an-email");
        request.setFirstName("");

        StepVerifier.create(validator.validateCreate(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RequestValidationException.class);
                    assertThat(((RequestValidationException) error).getErrors())
                            .containsKeys("email", "firstName", "lastName");
                })
                .verify();
    }

    @Test
    @DisplayName("email обрезается до проверки: пробелы не превращаются в 400")
    void trimsEmailBeforeValidation() {
        CreateUserRequest request = validCreateRequest();
        request.setEmail("  ivan.petrov@example.org  ");

        StepVerifier.create(validator.validateCreate(request))
                .assertNext(valid -> assertThat(valid.getEmail()).isEqualTo("ivan.petrov@example.org"))
                .verifyComplete();
    }

    @Test
    @DisplayName("PATCH без email проходит: поле необязательное")
    void acceptsUpdateWithoutEmail() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setLastName("Петров-Старший");

        StepVerifier.create(validator.validateUpdate(request))
                .assertNext(valid -> assertThat(valid.getLastName()).isEqualTo("Петров-Старший"))
                .verifyComplete();
    }

    @Test
    @DisplayName("PATCH с невалидным email отклоняется")
    void rejectsInvalidEmailOnUpdate() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("not-an-email");

        StepVerifier.create(validator.validateUpdate(request))
                .expectError(RequestValidationException.class)
                .verify();
    }

    private CreateUserRequest validCreateRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("ivan.petrov@example.org");
        request.setFirstName("Иван");
        request.setLastName("Петров");
        CreateAddressRequest address = new CreateAddressRequest();
        address.setCountryAlpha3("RUS");
        address.setCountryAlpha2("RU");
        address.setCity("Moscow");
        address.setAddressLine("ул. Пример, д. 1");
        request.setAddress(address);
        return request;
    }
}

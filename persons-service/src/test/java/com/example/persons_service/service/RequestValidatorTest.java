package com.example.persons_service.service;

import com.example.persons_service.exception.RequestValidationException;
import jakarta.validation.Validation;
import net.example.person.dto.CreateAddressRequest;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RequestValidator: проверка тела запроса и нормализация email")
class RequestValidatorTest {

    private final RequestValidator validator =
            new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    @DisplayName("корректный запрос проходит дальше без изменений")
    void acceptsValidCreateRequest() {
        CreateUserRequest request = validCreateRequest();

        CreateUserRequest valid = validator.validateCreate(request);

        assertThat(valid).isSameAs(request);
    }

    @Test
    @DisplayName("ошибки перечислены по именам полей")
    void reportsFieldErrors() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("not-an-email");
        request.setFirstName("");

        assertThatThrownBy(() -> validator.validateCreate(request))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(error -> assertThat(((RequestValidationException) error).getErrors())
                        .containsKeys("email", "firstName", "lastName"));
    }

    @Test
    @DisplayName("email обрезается до проверки: пробелы не превращаются в 400")
    void trimsEmailBeforeValidation() {
        CreateUserRequest request = validCreateRequest();
        request.setEmail("  ivan.petrov@example.org  ");

        CreateUserRequest valid = validator.validateCreate(request);

        assertThat(valid.getEmail()).isEqualTo("ivan.petrov@example.org");
    }

    @Test
    @DisplayName("PATCH без email проходит: поле необязательное")
    void acceptsUpdateWithoutEmail() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setLastName("Петров-Старший");

        UpdateUserRequest valid = validator.validateUpdate(request);

        assertThat(valid.getLastName()).isEqualTo("Петров-Старший");
    }

    @Test
    @DisplayName("PATCH с невалидным email отклоняется")
    void rejectsInvalidEmailOnUpdate() {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("not-an-email");

        assertThatThrownBy(() -> validator.validateUpdate(request))
                .isInstanceOf(RequestValidationException.class);
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

package com.example.persons_service.service;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import com.example.persons_service.exception.EmailAlreadyExistsException;
import com.example.persons_service.exception.RequestValidationException;
import com.example.persons_service.exception.UserNotFoundException;
import com.example.persons_service.mapper.PersonDtoMapper;
import com.example.persons_service.repository.UserAggregateStore;
import jakarta.validation.Validation;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PersonServiceImpl: сценарии и их порядок")
class PersonServiceImplTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-08T10:15:30Z");
    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    private UserAggregateStore store;

    @Mock
    private UserAggregateAssembler assembler;

    private PersonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonServiceImpl(
                store,
                assembler,
                new PersonDtoMapper(),
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator()),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("создание: проверка email → сборка агрегата → вставка → ответ")
    void createsAggregate() {
        CreateUserRequest request = createRequest();
        UserAggregate aggregate = aggregate();

        when(store.emailExists("ivan.petrov@example.org")).thenReturn(false);
        when(assembler.forCreate(request)).thenReturn(aggregate);
        when(store.insert(aggregate)).thenReturn(aggregate);

        UserResponse response = service.createUser(request);

        assertThat(response.getId()).isEqualTo(aggregate.user().getId());
        assertThat(response.getEmail()).isEqualTo("ivan.petrov@example.org");
        assertThat(response.getFilled()).isTrue();
        assertThat(response.getAddress().getCountryAlpha3()).isEqualTo("RUS");
    }

    @Test
    @DisplayName("создание: занятый email отклоняется до сборки агрегата")
    void rejectsDuplicateEmail() {
        CreateUserRequest request = createRequest();
        when(store.emailExists("ivan.petrov@example.org")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verifyNoInteractions(assembler);
        verify(store, never()).insert(any());
    }

    @Test
    @DisplayName("создание: невалидный запрос отклоняется до обращения к хранилищу")
    void rejectsInvalidRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("not-an-email");

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(error -> assertThat(((RequestValidationException) error).getErrors())
                        .containsKeys("email", "firstName", "lastName"));

        verifyNoInteractions(store, assembler);
    }

    @Test
    @DisplayName("чтение по id отдаёт агрегат из хранилища")
    void readsById() {
        UserAggregate aggregate = aggregate();
        when(store.loadById(aggregate.user().getId())).thenReturn(aggregate);

        assertThat(service.getUserById(aggregate.user().getId()).getAddress().getCity()).isEqualTo("Moscow");
    }

    @Test
    @DisplayName("чтение по email обрезает пробелы перед поиском")
    void readsByEmailWithTrimming() {
        UserAggregate aggregate = aggregate();
        when(store.loadByEmail("IVAN.PETROV@example.org")).thenReturn(aggregate);

        assertThat(service.getUserByEmail("  IVAN.PETROV@example.org ").getEmail())
                .isEqualTo("ivan.petrov@example.org");

        verify(store).loadByEmail("IVAN.PETROV@example.org");
    }

    @Test
    @DisplayName("чтение: 404 из хранилища пробрасывается как есть")
    void propagatesNotFound() {
        UUID id = UUID.randomUUID();
        when(store.loadById(id)).thenThrow(new UserNotFoundException(id));

        assertThatThrownBy(() -> service.getUserById(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("изменение: изменённый патч сохраняется")
    void appliesChangedPatch() {
        UserAggregate before = aggregate();
        UpdateUserRequest request = new UpdateUserRequest();
        request.setLastName("Петров-Старший");

        UserAggregate after = aggregateWithLastName("Петров-Старший");
        AggregatePatch patch = new AggregatePatch(
                before, after, true,
                new AggregatePatch.AddressChange(after.address(), false, false),
                new AggregatePatch.IndividualChange(after.individual(), false, false));

        when(store.loadById(before.user().getId())).thenReturn(before);
        when(assembler.forUpdate(before, request)).thenReturn(patch);
        when(store.update(patch)).thenReturn(after);

        UserResponse response = service.updateUser(before.user().getId(), request);

        assertThat(response.getLastName()).isEqualTo("Петров-Старший");
        assertThat(response.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("изменение: пустой патч не пишется")
    void skipsNoOpPatch() {
        UserAggregate before = aggregate();
        UpdateUserRequest request = new UpdateUserRequest();
        AggregatePatch patch = new AggregatePatch(before, before, false, null, null);

        when(store.loadById(before.user().getId())).thenReturn(before);
        when(assembler.forUpdate(before, request)).thenReturn(patch);

        assertThat(service.updateUser(before.user().getId(), request).getLastName()).isEqualTo("Петров");

        verify(store, never()).update(any());
    }

    @Test
    @DisplayName("изменение: смена email на занятый отклоняется до записи")
    void rejectsEmailTakenByAnotherUser() {
        UserAggregate before = aggregate();
        UserAggregate after = aggregateWithEmail("other@example.org");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("other@example.org");

        AggregatePatch patch = new AggregatePatch(
                before, after, true,
                new AggregatePatch.AddressChange(after.address(), false, false),
                new AggregatePatch.IndividualChange(after.individual(), false, false));

        when(store.loadById(before.user().getId())).thenReturn(before);
        when(assembler.forUpdate(before, request)).thenReturn(patch);
        when(store.emailExists("other@example.org")).thenReturn(true);

        assertThatThrownBy(() -> service.updateUser(before.user().getId(), request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(store, never()).update(any());
    }

    @Test
    @DisplayName("изменение: тот же email в другом регистре не считается конфликтом")
    void allowsSameEmailDifferentCase() {
        UserAggregate before = aggregate();
        UserAggregate after = aggregateWithEmail("IVAN.PETROV@example.org");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("IVAN.PETROV@example.org");

        AggregatePatch patch = new AggregatePatch(
                before, after, true,
                new AggregatePatch.AddressChange(after.address(), false, false),
                new AggregatePatch.IndividualChange(after.individual(), false, false));

        when(store.loadById(before.user().getId())).thenReturn(before);
        when(assembler.forUpdate(before, request)).thenReturn(patch);
        when(store.update(patch)).thenReturn(after);

        assertThat(service.updateUser(before.user().getId(), request).getEmail())
                .isEqualTo("IVAN.PETROV@example.org");

        verify(store, never()).emailExists(any());
    }

    @Test
    @DisplayName("хард-удаление: физическое удаление агрегата")
    void hardDeletesAggregate() {
        UserAggregate aggregate = aggregate();
        when(store.loadById(aggregate.user().getId())).thenReturn(aggregate);

        service.deleteUser(aggregate.user().getId(), false);

        verify(store).delete(aggregate);
        assertThat(aggregate.user().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("софт-удаление: строка не удаляется, проставляется deletedAt")
    void softDeletesAggregate() {
        UserAggregate aggregate = aggregate();
        when(store.loadById(aggregate.user().getId())).thenReturn(aggregate);

        service.deleteUser(aggregate.user().getId(), true);

        assertThat(aggregate.user().getDeletedAt()).isEqualTo(NOW);
        verify(store, never()).delete(any());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private CreateUserRequest createRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("ivan.petrov@example.org");
        request.setFirstName("Иван");
        request.setLastName("Петров");
        return request;
    }

    private UserAggregate aggregate() {
        return aggregateWith("ivan.petrov@example.org", "Петров");
    }

    private UserAggregate aggregateWithEmail(String email) {
        return aggregateWith(email, "Петров");
    }

    private UserAggregate aggregateWithLastName(String lastName) {
        return aggregateWith("ivan.petrov@example.org", lastName);
    }

    private UserAggregate aggregateWith(String email, String lastName) {
        CountryEntity country = new CountryEntity();
        country.setId(1);
        country.setAlpha3("RUS");
        country.setAlpha2("RU");

        AddressEntity address = AddressEntity.newInstance();
        address.setCountryId(country.getId());
        address.setCity("Moscow");
        address.setAddress("ул. Пример, д. 1");
        address.setCreated(NOW);
        address.setUpdated(NOW);

        UserEntity user = UserEntity.newInstance();
        user.setEmail(email);
        user.setFirstName("Иван");
        user.setLastName(lastName);
        user.setCreated(NOW);
        user.setUpdated(NOW);
        user.setAddressId(address.getId());
        user.setFilled(true);

        IndividualEntity individual = IndividualEntity.newInstance();
        individual.setUserId(user.getId());
        individual.setPhoneNumber("+79991234567");

        return new UserAggregate(user, address, individual, country);
    }
}

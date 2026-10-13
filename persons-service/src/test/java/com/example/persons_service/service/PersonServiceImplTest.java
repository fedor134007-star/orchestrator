package com.example.persons_service.service;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.audit.AuditRecorder;
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

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 6, 8, 10, 15, 30, 0, ZoneOffset.UTC);

    @Mock
    private UserAggregateStore store;

    @Mock
    private UserAggregateAssembler assembler;

    @Mock
    private AuditRecorder auditRecorder;

    private PersonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonServiceImpl(
                store,
                assembler,
                auditRecorder,
                new PersonDtoMapper(),
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator()));
    }

    @Test
    @DisplayName("создание: проверка email → сборка агрегата → вставка → ревизия → ответ")
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

        verify(auditRecorder).recordCreated(aggregate);
    }

    @Test
    @DisplayName("создание: занятый email отклоняется до сборки агрегата")
    void rejectsDuplicateEmail() {
        CreateUserRequest request = createRequest();
        when(store.emailExists("ivan.petrov@example.org")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verifyNoInteractions(assembler, auditRecorder);
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

        verifyNoInteractions(store, assembler, auditRecorder);
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
    @DisplayName("изменение: изменённый патч сохраняется и попадает в аудит")
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

        verify(auditRecorder).recordUpdated(patch, after);
    }

    @Test
    @DisplayName("изменение: пустой патч не пишется и не создаёт ревизию")
    void skipsNoOpPatch() {
        UserAggregate before = aggregate();
        UpdateUserRequest request = new UpdateUserRequest();
        AggregatePatch patch = new AggregatePatch(before, before, false, null, null);

        when(store.loadById(before.user().getId())).thenReturn(before);
        when(assembler.forUpdate(before, request)).thenReturn(patch);

        assertThat(service.updateUser(before.user().getId(), request).getLastName()).isEqualTo("Петров");

        verify(store, never()).update(any());
        verifyNoInteractions(auditRecorder);
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
        verifyNoInteractions(auditRecorder);
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
    @DisplayName("удаление: ревизия пишется до удаления строк, в той же транзакции")
    void deletesAggregate() {
        UserAggregate aggregate = aggregate();
        when(store.loadById(aggregate.user().getId())).thenReturn(aggregate);

        service.deleteUser(aggregate.user().getId());

        verify(auditRecorder).recordDeleted(aggregate);
        verify(store).delete(aggregate);
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

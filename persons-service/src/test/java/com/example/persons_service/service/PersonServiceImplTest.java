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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

        when(store.emailExists("ivan.petrov@example.org")).thenReturn(Mono.just(false));
        when(assembler.forCreate(request)).thenReturn(Mono.just(aggregate));
        when(store.insert(aggregate)).thenReturn(Mono.just(aggregate));
        when(auditRecorder.recordCreated(aggregate)).thenReturn(Mono.empty());

        StepVerifier.create(service.createUser(request))
                .assertNext(response -> {
                    assertThat(response.getId()).isEqualTo(aggregate.user().getId());
                    assertThat(response.getEmail()).isEqualTo("ivan.petrov@example.org");
                    assertThat(response.getFilled()).isTrue();
                    assertThat(response.getAddress().getCountryAlpha3()).isEqualTo("RUS");
                })
                .verifyComplete();

        verify(auditRecorder).recordCreated(aggregate);
    }

    @Test
    @DisplayName("создание: занятый email отклоняется до сборки агрегата")
    void rejectsDuplicateEmail() {
        CreateUserRequest request = createRequest();
        when(store.emailExists("ivan.petrov@example.org")).thenReturn(Mono.just(true));

        StepVerifier.create(service.createUser(request))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

        verifyNoInteractions(assembler, auditRecorder);
        verify(store, never()).insert(any());
    }

    @Test
    @DisplayName("создание: невалидный запрос отклоняется до обращения к хранилищу")
    void rejectsInvalidRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("not-an-email");

        StepVerifier.create(service.createUser(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(RequestValidationException.class);
                    assertThat(((RequestValidationException) error).getErrors())
                            .containsKeys("email", "firstName", "lastName");
                })
                .verify();

        verifyNoInteractions(store, assembler, auditRecorder);
    }

    @Test
    @DisplayName("чтение по id отдаёт агрегат из хранилища")
    void readsById() {
        UserAggregate aggregate = aggregate();
        when(store.loadById(aggregate.user().getId())).thenReturn(Mono.just(aggregate));

        StepVerifier.create(service.getUserById(aggregate.user().getId()))
                .assertNext(response -> assertThat(response.getAddress().getCity()).isEqualTo("Moscow"))
                .verifyComplete();
    }

    @Test
    @DisplayName("чтение по email обрезает пробелы перед поиском")
    void readsByEmailWithTrimming() {
        UserAggregate aggregate = aggregate();
        when(store.loadByEmail("IVAN.PETROV@example.org")).thenReturn(Mono.just(aggregate));

        StepVerifier.create(service.getUserByEmail("  IVAN.PETROV@example.org "))
                .assertNext(response -> assertThat(response.getEmail()).isEqualTo("ivan.petrov@example.org"))
                .verifyComplete();

        verify(store).loadByEmail("IVAN.PETROV@example.org");
    }

    @Test
    @DisplayName("чтение: 404 из хранилища пробрасывается как есть")
    void propagatesNotFound() {
        UUID id = UUID.randomUUID();
        when(store.loadById(id)).thenReturn(Mono.error(new UserNotFoundException(id)));

        StepVerifier.create(service.getUserById(id))
                .expectError(UserNotFoundException.class)
                .verify();
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

        when(store.loadById(before.user().getId())).thenReturn(Mono.just(before));
        when(assembler.forUpdate(before, request)).thenReturn(Mono.just(patch));
        when(store.update(patch)).thenReturn(Mono.just(after));
        when(auditRecorder.recordUpdated(patch, after)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateUser(before.user().getId(), request))
                .assertNext(response -> {
                    assertThat(response.getLastName()).isEqualTo("Петров-Старший");
                    assertThat(response.getUpdatedAt()).isEqualTo(NOW);
                })
                .verifyComplete();

        verify(auditRecorder).recordUpdated(patch, after);
    }

    @Test
    @DisplayName("изменение: пустой патч не пишется и не создаёт ревизию")
    void skipsNoOpPatch() {
        UserAggregate before = aggregate();
        UpdateUserRequest request = new UpdateUserRequest();
        AggregatePatch patch = new AggregatePatch(before, before, false, null, null);

        when(store.loadById(before.user().getId())).thenReturn(Mono.just(before));
        when(assembler.forUpdate(before, request)).thenReturn(Mono.just(patch));

        StepVerifier.create(service.updateUser(before.user().getId(), request))
                .assertNext(response -> assertThat(response.getLastName()).isEqualTo("Петров"))
                .verifyComplete();

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

        when(store.loadById(before.user().getId())).thenReturn(Mono.just(before));
        when(assembler.forUpdate(before, request)).thenReturn(Mono.just(patch));
        when(store.emailExists("other@example.org")).thenReturn(Mono.just(true));

        StepVerifier.create(service.updateUser(before.user().getId(), request))
                .expectError(EmailAlreadyExistsException.class)
                .verify();

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

        when(store.loadById(before.user().getId())).thenReturn(Mono.just(before));
        when(assembler.forUpdate(before, request)).thenReturn(Mono.just(patch));
        when(store.update(patch)).thenReturn(Mono.just(after));
        when(auditRecorder.recordUpdated(patch, after)).thenReturn(Mono.empty());

        StepVerifier.create(service.updateUser(before.user().getId(), request))
                .assertNext(response -> assertThat(response.getEmail()).isEqualTo("IVAN.PETROV@example.org"))
                .verifyComplete();

        verify(store, never()).emailExists(any());
    }

    @Test
    @DisplayName("удаление: ревизия пишется до удаления строк, в той же транзакции")
    void deletesAggregate() {
        UserAggregate aggregate = aggregate();
        when(store.loadById(aggregate.user().getId())).thenReturn(Mono.just(aggregate));
        when(auditRecorder.recordDeleted(aggregate)).thenReturn(Mono.empty());
        when(store.delete(aggregate)).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteUser(aggregate.user().getId()))
                .verifyComplete();

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

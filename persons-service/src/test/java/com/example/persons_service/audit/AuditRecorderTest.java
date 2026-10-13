package com.example.persons_service.audit;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.IndividualStatus;
import com.example.persons_service.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditRecorder: одна ревизия на операцию")
class AuditRecorderTest {

    private static final int REVISION = 7;

    @Mock
    private AuditWriter auditWriter;

    private AuditRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new AuditRecorder(auditWriter);
        lenient().when(auditWriter.openRevision()).thenReturn(Mono.just(REVISION));
        lenient().when(auditWriter.writeUser(anyInt(), anyShort(), any(), any(), anyBoolean()))
                .thenReturn(Mono.empty());
        lenient().when(auditWriter.writeAddress(anyInt(), anyShort(), any())).thenReturn(Mono.empty());
        lenient().when(auditWriter.writeIndividual(anyInt(), anyShort(), any(), any()))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("создание: INSERT по всем трём сущностям в одной ревизии")
    void recordsCreation() {
        UserAggregate aggregate = fullAggregate();

        StepVerifier.create(recorder.recordCreated(aggregate))
                .verifyComplete();

        verify(auditWriter).writeUser(REVISION, AuditWriter.INSERT, aggregate.user(), aggregate.user(), true);
        verify(auditWriter).writeAddress(REVISION, AuditWriter.INSERT, aggregate.address());
        verify(auditWriter).writeIndividual(REVISION, AuditWriter.INSERT, aggregate.individual(), aggregate.individual());
    }

    @Test
    @DisplayName("создание без вложенных сущностей: только запись пользователя")
    void recordsCreationWithoutNestedEntities() {
        UserAggregate aggregate = new UserAggregate(user(), null, null, null);

        StepVerifier.create(recorder.recordCreated(aggregate))
                .verifyComplete();

        verify(auditWriter).writeUser(REVISION, AuditWriter.INSERT, aggregate.user(), aggregate.user(), false);
        verify(auditWriter, never()).writeAddress(anyInt(), anyShort(), any());
        verify(auditWriter, never()).writeIndividual(anyInt(), anyShort(), any(), any());
    }

    @Test
    @DisplayName("изменение: MOD по затронутым сущностям, незатронутые не пишутся")
    void recordsUpdate() {
        UserAggregate before = fullAggregate();
        UserAggregate after = fullAggregate();
        after.user().setLastName("Петров-Старший");

        AggregatePatch patch = new AggregatePatch(
                before, after, true,
                new AggregatePatch.AddressChange(after.address(), false, true),
                new AggregatePatch.IndividualChange(after.individual(), false, false));

        StepVerifier.create(recorder.recordUpdated(patch, after))
                .verifyComplete();

        verify(auditWriter).writeUser(REVISION, AuditWriter.UPDATE, before.user(), after.user(), false);
        verify(auditWriter).writeAddress(REVISION, AuditWriter.UPDATE, after.address());
        verify(auditWriter, never()).writeIndividual(anyInt(), anyShort(), any(), any());
    }

    @Test
    @DisplayName("изменение: созданные адрес и индивидуальные данные пишутся как INSERT")
    void recordsUpdateWithNewNestedEntities() {
        UserAggregate before = fullAggregate();
        UserAggregate after = fullAggregate();

        AggregatePatch patch = new AggregatePatch(
                before, after, true,
                new AggregatePatch.AddressChange(after.address(), true, true),
                new AggregatePatch.IndividualChange(after.individual(), true, true));

        StepVerifier.create(recorder.recordUpdated(patch, after))
                .verifyComplete();

        verify(auditWriter).writeUser(REVISION, AuditWriter.UPDATE, before.user(), after.user(), true);
        verify(auditWriter).writeAddress(REVISION, AuditWriter.INSERT, after.address());
        verify(auditWriter).writeIndividual(REVISION, AuditWriter.INSERT, after.individual(), after.individual());
    }

    @Test
    @DisplayName("удаление: DELETE по всем трём сущностям (снимок состояния)")
    void recordsDeletion() {
        UserAggregate aggregate = fullAggregate();

        StepVerifier.create(recorder.recordDeleted(aggregate))
                .verifyComplete();

        verify(auditWriter).writeUser(REVISION, AuditWriter.DELETE, aggregate.user(), aggregate.user(), true);
        verify(auditWriter).writeAddress(REVISION, AuditWriter.DELETE, aggregate.address());
        verify(auditWriter).writeIndividual(REVISION, AuditWriter.DELETE, aggregate.individual(), aggregate.individual());
    }

    @Test
    @DisplayName("если ревизию открыть не удалось, ошибка пробрасывается")
    void propagatesRevisionFailure() {
        when(auditWriter.openRevision()).thenReturn(Mono.error(new IllegalStateException("нет соединения")));

        StepVerifier.create(recorder.recordCreated(fullAggregate()))
                .expectError(IllegalStateException.class)
                .verify();

        verify(auditWriter, never()).writeUser(anyInt(), anyShort(), any(), any(), anyBoolean());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private UserAggregate fullAggregate() {
        CountryEntity country = new CountryEntity();
        country.setId(1);
        country.setAlpha3("RUS");
        country.setAlpha2("RU");

        AddressEntity address = AddressEntity.newInstance();
        address.setCountryId(1);
        address.setCity("Moscow");
        address.setAddress("ул. Пример, д. 1");

        IndividualEntity individual = IndividualEntity.newInstance();
        individual.setStatus(IndividualStatus.NEW);
        individual.setPhoneNumber("+79991234567");

        UserEntity user = user();
        user.setAddressId(address.getId());
        user.setFilled(true);
        individual.setUserId(user.getId());

        return new UserAggregate(user, address, individual, country);
    }

    private UserEntity user() {
        UserEntity user = UserEntity.newInstance();
        user.setEmail("ivan.petrov@example.org");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        return user;
    }
}

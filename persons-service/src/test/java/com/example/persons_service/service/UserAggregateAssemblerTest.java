package com.example.persons_service.service;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.IndividualStatus;
import com.example.persons_service.entity.UserEntity;
import com.example.persons_service.exception.IncompleteAggregateUpdateException;
import com.example.persons_service.exception.UnknownCountryException;
import com.example.persons_service.mapper.PersonDtoMapper;
import com.example.persons_service.repository.CountryRepository;
import net.example.person.dto.CreateAddressRequest;
import net.example.person.dto.CreateIndividualRequest;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateAddressRequest;
import net.example.person.dto.UpdateIndividualRequest;
import net.example.person.dto.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAggregateAssembler: политика агрегата")
class UserAggregateAssemblerTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-06-08T10:15:30Z");
    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    private CountryRepository countryRepository;

    private UserAggregateAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new UserAggregateAssembler(
                new CountryResolver(countryRepository),
                new PersonDtoMapper(),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------
    // Создание
    // ------------------------------------------------------------------

    @Test
    @DisplayName("создание с адресом и индивидуальными данными: filled=true, связи выставлены")
    void assemblesNewAggregate() {
        when(countryRepository.findByAlpha3IgnoreCase("RUS")).thenReturn(Mono.just(country()));

        StepVerifier.create(assembler.forCreate(createRequest(true, true)))
                .assertNext(aggregate -> {
                    UserEntity user = aggregate.user();
                    assertThat(user.getEmail()).isEqualTo("ivan.petrov@example.org");
                    assertThat(user.getFirstName()).isEqualTo("Иван");
                    assertThat(user.getLastName()).isEqualTo("Петров");
                    assertThat(user.getCreated()).isEqualTo(NOW);
                    assertThat(user.getUpdated()).isEqualTo(NOW);
                    assertThat(user.isFilled()).isTrue();
                    assertThat(user.getAddressId()).isEqualTo(aggregate.address().getId());

                    assertThat(aggregate.address().getCountryId()).isEqualTo(1);
                    assertThat(aggregate.address().getCity()).isEqualTo("Moscow");
                    assertThat(aggregate.address().getAddress()).isEqualTo("ул. Пример, д. 1");
                    assertThat(aggregate.address().getCreated()).isEqualTo(NOW);

                    assertThat(aggregate.individual().getUserId()).isEqualTo(user.getId());
                    assertThat(aggregate.individual().getStatus()).isEqualTo(IndividualStatus.NEW);
                    assertThat(aggregate.individual().getPhoneNumber()).isEqualTo("+79991234567");

                    assertThat(aggregate.country().getAlpha3()).isEqualTo("RUS");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("создание без адреса: filled=false и справочник не опрашивается")
    void assemblesAggregateWithoutAddress() {
        StepVerifier.create(assembler.forCreate(createRequest(false, true)))
                .assertNext(aggregate -> {
                    assertThat(aggregate.address()).isNull();
                    assertThat(aggregate.country()).isNull();
                    assertThat(aggregate.user().isFilled()).isFalse();
                    assertThat(aggregate.user().getAddressId()).isNull();
                })
                .verifyComplete();

        verifyNoInteractions(countryRepository);
    }

    @Test
    @DisplayName("создание: неизвестная страна отклоняется")
    void rejectsUnknownCountry() {
        when(countryRepository.findByAlpha3IgnoreCase("ZZZ")).thenReturn(Mono.empty());

        CreateUserRequest request = createRequest(true, false);
        request.getAddress().setCountryAlpha3("ZZZ");
        request.getAddress().setCountryAlpha2("ZZ");

        StepVerifier.create(assembler.forCreate(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UnknownCountryException.class);
                    assertThat(error).hasMessageContaining("ZZZ");
                })
                .verify();
    }

    @Test
    @DisplayName("создание: адрес без кода страны — неполный агрегат")
    void rejectsAddressWithoutCountryCode() {
        CreateUserRequest request = createRequest(true, false);
        request.getAddress().setCountryAlpha3(null);
        request.getAddress().setCountryAlpha2(null);

        StepVerifier.create(assembler.forCreate(request))
                .expectError(IncompleteAggregateUpdateException.class)
                .verify();
    }

    @Test
    @DisplayName("создание: коды alpha-2 и alpha-3 должны быть согласованы")
    void rejectsMismatchedCountryCodes() {
        when(countryRepository.findByAlpha3IgnoreCase("RUS")).thenReturn(Mono.just(country()));

        CreateUserRequest request = createRequest(true, false);
        request.getAddress().setCountryAlpha2("DE");

        StepVerifier.create(assembler.forCreate(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UnknownCountryException.class);
                    assertThat(error).hasMessageContaining("противоречат");
                })
                .verify();
    }

    // ------------------------------------------------------------------
    // Изменение
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PATCH меняет только переданные поля и помечает затронутые сущности")
    void appliesPatchToCopies() {
        UserAggregate before = fullAggregate();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setLastName("Петров-Старший");
        UpdateAddressRequest addressRequest = new UpdateAddressRequest();
        addressRequest.setCity("Saint Petersburg");
        request.setAddress(addressRequest);
        UpdateIndividualRequest individualRequest = new UpdateIndividualRequest();
        individualRequest.setPhoneNumber("+79990000000");
        request.setIndividual(individualRequest);

        StepVerifier.create(assembler.forUpdate(before, request))
                .assertNext(patch -> {
                    assertThat(patch.changed()).isTrue();
                    assertThat(patch.after().user().getLastName()).isEqualTo("Петров-Старший");
                    assertThat(patch.after().user().getUpdated()).isEqualTo(NOW);
                    assertThat(patch.after().address().getCity()).isEqualTo("Saint Petersburg");
                    assertThat(patch.after().address().getAddress()).isEqualTo("ул. Пример, д. 1");
                    assertThat(patch.after().individual().getPhoneNumber()).isEqualTo("+79990000000");
                    assertThat(patch.after().individual().getPassportNumber()).isEqualTo("1234 567890");

                    assertThat(patch.address().touched()).isTrue();
                    assertThat(patch.address().created()).isFalse();
                    assertThat(patch.individual().touched()).isTrue();
                    assertThat(patch.individual().created()).isFalse();
                })
                .verifyComplete();

        // исходный агрегат не мутирован: его состояние нужно и для аудита, и для сравнения
        assertThat(before.user().getLastName()).isEqualTo("Петров");
        assertThat(before.address().getCity()).isEqualTo("Moscow");
    }

    @Test
    @DisplayName("PATCH создаёт отсутствующие адрес и индивидуальные данные")
    void createsMissingNestedEntities() {
        UserAggregate before = aggregateWithoutNestedEntities();
        when(countryRepository.findByAlpha3IgnoreCase("RUS")).thenReturn(Mono.just(country()));

        UpdateUserRequest request = new UpdateUserRequest();
        UpdateAddressRequest addressRequest = new UpdateAddressRequest();
        addressRequest.setCountryAlpha3("RUS");
        addressRequest.setCountryAlpha2("RU");
        addressRequest.setCity("Moscow");
        addressRequest.setAddressLine("ул. Новая, д. 2");
        request.setAddress(addressRequest);
        UpdateIndividualRequest individualRequest = new UpdateIndividualRequest();
        individualRequest.setPhoneNumber("+79991112233");
        request.setIndividual(individualRequest);

        StepVerifier.create(assembler.forUpdate(before, request))
                .assertNext(patch -> {
                    assertThat(patch.changed()).isTrue();
                    assertThat(patch.address().created()).isTrue();
                    assertThat(patch.address().touched()).isTrue();
                    assertThat(patch.individual().created()).isTrue();
                    assertThat(patch.after().address().getCountryId()).isEqualTo(1);
                    assertThat(patch.after().user().getAddressId()).isEqualTo(patch.after().address().getId());
                    assertThat(patch.after().individual().getUserId()).isEqualTo(before.user().getId());
                    assertThat(patch.after().user().isFilled()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PATCH не позволяет создать неполный адрес")
    void rejectsIncompleteNewAddress() {
        UserAggregate before = aggregateWithoutNestedEntities();

        UpdateUserRequest request = new UpdateUserRequest();
        UpdateAddressRequest addressRequest = new UpdateAddressRequest();
        addressRequest.setCity("Moscow");
        request.setAddress(addressRequest);

        StepVerifier.create(assembler.forUpdate(before, request))
                .expectError(IncompleteAggregateUpdateException.class)
                .verify();
    }

    @Test
    @DisplayName("пустой PATCH не считается изменением: updatedAt не сдвигается")
    void treatsEmptyPatchAsNoChange() {
        UserAggregate before = fullAggregate();

        StepVerifier.create(assembler.forUpdate(before, new UpdateUserRequest()))
                .assertNext(patch -> {
                    assertThat(patch.changed()).isFalse();
                    assertThat(patch.after()).isSameAs(before);
                    assertThat(patch.address()).isNull();
                    assertThat(patch.individual()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PATCH с теми же значениями не считается изменением")
    void treatsSameValuesAsNoChange() {
        UserAggregate before = fullAggregate();

        UpdateUserRequest request = new UpdateUserRequest();
        request.setLastName("Петров");
        UpdateAddressRequest addressRequest = new UpdateAddressRequest();
        addressRequest.setCity("Moscow");
        request.setAddress(addressRequest);

        StepVerifier.create(assembler.forUpdate(before, request))
                .assertNext(patch -> assertThat(patch.changed()).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("PATCH может перевести адрес в другую страну")
    void changesAddressCountry() {
        UserAggregate before = fullAggregate();
        CountryEntity germany = new CountryEntity();
        germany.setId(2);
        germany.setAlpha3("DEU");
        germany.setAlpha2("DE");
        when(countryRepository.findByAlpha3IgnoreCase("DEU")).thenReturn(Mono.just(germany));

        UpdateUserRequest request = new UpdateUserRequest();
        UpdateAddressRequest addressRequest = new UpdateAddressRequest();
        addressRequest.setCountryAlpha3("DEU");
        addressRequest.setCountryAlpha2("DE");
        request.setAddress(addressRequest);

        StepVerifier.create(assembler.forUpdate(before, request))
                .assertNext(patch -> {
                    assertThat(patch.changed()).isTrue();
                    assertThat(patch.address().touched()).isTrue();
                    assertThat(patch.after().address().getCountryId()).isEqualTo(2);
                    assertThat(patch.after().country().getAlpha3()).isEqualTo("DEU");
                })
                .verifyComplete();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private CountryEntity country() {
        CountryEntity country = new CountryEntity();
        country.setId(1);
        country.setAlpha3("RUS");
        country.setAlpha2("RU");
        country.setName("Russia");
        country.setStatus("ACTIVE");
        return country;
    }

    private CreateUserRequest createRequest(boolean withAddress, boolean withIndividual) {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("ivan.petrov@example.org");
        request.setFirstName("Иван");
        request.setLastName("Петров");
        if (withAddress) {
            CreateAddressRequest address = new CreateAddressRequest();
            address.setCountryAlpha3("RUS");
            address.setCountryAlpha2("RU");
            address.setCity("Moscow");
            address.setState("Moscow");
            address.setZipCode("101000");
            address.setAddressLine("ул. Пример, д. 1");
            request.setAddress(address);
        }
        if (withIndividual) {
            CreateIndividualRequest individual = new CreateIndividualRequest();
            individual.setPassportNumber("1234 567890");
            individual.setPhoneNumber("+79991234567");
            request.setIndividual(individual);
        }
        return request;
    }

    private UserAggregate fullAggregate() {
        CountryEntity country = country();

        AddressEntity address = AddressEntity.newInstance();
        address.setCountryId(country.getId());
        address.setCity("Moscow");
        address.setState("Moscow");
        address.setZipCode("101000");
        address.setAddress("ул. Пример, д. 1");
        address.setCreated(NOW);
        address.setUpdated(NOW);

        UserEntity user = UserEntity.newInstance();
        user.setEmail("ivan.petrov@example.org");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setCreated(NOW);
        user.setUpdated(NOW);
        user.setAddressId(address.getId());
        user.setFilled(true);

        IndividualEntity individual = IndividualEntity.newInstance();
        individual.setUserId(user.getId());
        individual.setStatus(IndividualStatus.NEW);
        individual.setPassportNumber("1234 567890");
        individual.setPhoneNumber("+79991234567");

        return new UserAggregate(user, address, individual, country);
    }

    private UserAggregate aggregateWithoutNestedEntities() {
        UserEntity user = UserEntity.newInstance();
        user.setEmail("ivan.petrov@example.org");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setCreated(NOW);
        user.setUpdated(NOW);
        user.setFilled(false);
        return new UserAggregate(user, null, null, null);
    }
}

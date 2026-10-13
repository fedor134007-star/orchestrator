package com.example.persons_service.service;

import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.UserEntity;
import com.example.persons_service.exception.IncompleteAggregateUpdateException;
import com.example.persons_service.exception.UnknownCountryException;
import com.example.persons_service.repository.CountryRepository;
import net.example.person.dto.CreateAddressRequest;
import net.example.person.dto.UpdateAddressRequest;
import net.example.person.dto.UpdateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CountryResolver: справочник и согласованность кодов")
class CountryResolverTest {

    @Mock
    private CountryRepository countryRepository;

    private CountryResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CountryResolver(countryRepository);
    }

    @Test
    @DisplayName("создание без адреса: страна не нужна и справочник не опрашивается")
    void noCountryForCreateWithoutAddress() {
        StepVerifier.create(resolver.forCreate(null))
                .verifyComplete();

        verifyNoInteractions(countryRepository);
    }

    @Test
    @DisplayName("создание: страна находится по alpha-3")
    void resolvesByAlpha3() {
        when(countryRepository.findByAlpha3IgnoreCase("RUS")).thenReturn(Mono.just(country("RUS", "RU")));

        StepVerifier.create(resolver.forCreate(createAddress("RUS", "RU")))
                .assertNext(country -> assertThat(country.getAlpha2()).isEqualTo("RU"))
                .verifyComplete();
    }

    @Test
    @DisplayName("создание: страна находится по alpha-2, если alpha-3 не передан")
    void resolvesByAlpha2() {
        when(countryRepository.findByAlpha2IgnoreCase("RU")).thenReturn(Mono.just(country("RUS", "RU")));

        StepVerifier.create(resolver.forCreate(createAddress(null, "RU")))
                .assertNext(found -> assertThat(found.getAlpha3()).isEqualTo("RUS"))
                .verifyComplete();
    }

    @Test
    @DisplayName("создание: неизвестная страна")
    void unknownCountryOnCreate() {
        when(countryRepository.findByAlpha3IgnoreCase("ZZZ")).thenReturn(Mono.empty());

        StepVerifier.create(resolver.forCreate(createAddress("ZZZ", "ZZ")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UnknownCountryException.class);
                    assertThat(error).hasMessageContaining("ZZZ");
                })
                .verify();
    }

    @Test
    @DisplayName("создание: коды не переданы — неполный агрегат")
    void missingCodesOnCreate() {
        StepVerifier.create(resolver.forCreate(createAddress(null, null)))
                .expectError(IncompleteAggregateUpdateException.class)
                .verify();
    }

    @Test
    @DisplayName("изменение: коды противоречат друг другу")
    void mismatchedCodes() {
        when(countryRepository.findByAlpha3IgnoreCase("RUS")).thenReturn(Mono.just(country("RUS", "RU")));

        StepVerifier.create(resolver.forCreate(createAddress("RUS", "DE")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(UnknownCountryException.class);
                    assertThat(error).hasMessageContaining("противоречат");
                })
                .verify();
    }

    @Test
    @DisplayName("изменение без адреса в запросе: страна не нужна")
    void noCountryForUpdateWithoutAddress() {
        StepVerifier.create(resolver.forUpdate(aggregateWithAddress(), new UpdateUserRequest()))
                .verifyComplete();

        verifyNoInteractions(countryRepository);
    }

    @Test
    @DisplayName("изменение: адрес есть, коды не переданы — оставляем текущую страну")
    void keepsCurrentCountryWhenCodesMissing() {
        UpdateUserRequest request = new UpdateUserRequest();
        UpdateAddressRequest address = new UpdateAddressRequest();
        address.setCity("Saint Petersburg");
        request.setAddress(address);

        StepVerifier.create(resolver.forUpdate(aggregateWithAddress(), request))
                .verifyComplete();

        verifyNoInteractions(countryRepository);
    }

    @Test
    @DisplayName("изменение: создание адреса без кода страны — неполный агрегат")
    void missingCodesForNewAddressOnUpdate() {
        UpdateUserRequest request = new UpdateUserRequest();
        UpdateAddressRequest address = new UpdateAddressRequest();
        address.setCity("Moscow");
        request.setAddress(address);

        StepVerifier.create(resolver.forUpdate(aggregateWithoutAddress(), request))
                .expectError(IncompleteAggregateUpdateException.class)
                .verify();
    }

    @Test
    @DisplayName("изменение: переданный код резолвится")
    void resolvesOnUpdate() {
        when(countryRepository.findByAlpha3IgnoreCase("DEU")).thenReturn(Mono.just(country("DEU", "DE")));

        UpdateUserRequest request = new UpdateUserRequest();
        UpdateAddressRequest address = new UpdateAddressRequest();
        address.setCountryAlpha3("DEU");
        address.setCountryAlpha2("DE");
        request.setAddress(address);

        StepVerifier.create(resolver.forUpdate(aggregateWithAddress(), request))
                .assertNext(found -> assertThat(found.getAlpha2()).isEqualTo("DE"))
                .verifyComplete();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private CountryEntity country(String alpha3, String alpha2) {
        CountryEntity country = new CountryEntity();
        country.setId(1);
        country.setAlpha3(alpha3);
        country.setAlpha2(alpha2);
        return country;
    }

    private CreateAddressRequest createAddress(String alpha3, String alpha2) {
        CreateAddressRequest address = new CreateAddressRequest();
        address.setCountryAlpha3(alpha3);
        address.setCountryAlpha2(alpha2);
        address.setCity("Moscow");
        address.setAddressLine("ул. Пример, д. 1");
        return address;
    }

    private UserAggregate aggregateWithAddress() {
        AddressEntity address = AddressEntity.newInstance();
        address.setCountryId(1);
        address.setCity("Moscow");
        address.setAddress("ул. Пример, д. 1");
        return new UserAggregate(user(), address, null, country("RUS", "RU"));
    }

    private UserAggregate aggregateWithoutAddress() {
        return new UserAggregate(user(), null, null, null);
    }

    private UserEntity user() {
        UserEntity user = UserEntity.newInstance();
        user.setEmail("ivan.petrov@example.org");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        return user;
    }
}

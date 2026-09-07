package com.example.persons_service.mapper;

import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.IndividualStatus;
import com.example.persons_service.entity.UserEntity;
import net.example.person.dto.AddressResponse;
import net.example.person.dto.IndividualResponse;
import net.example.person.dto.UpdateAddressRequest;
import net.example.person.dto.UpdateIndividualRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersonDtoMapper: отображение агрегата в DTO и patch-семантика")
class PersonDtoMapperTest {

    private final PersonDtoMapper mapper = new PersonDtoMapper();

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 8, 10, 15, 30, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("полный агрегат отображается целиком, включая коды страны и статус")
    void mapsFullAggregate() {
        CountryEntity country = country("RUS", "RU");
        AddressEntity address = address(country);
        IndividualEntity individual = individual();
        UserEntity user = user(address, individual);

        UserResponse response = mapper.toResponse(new UserAggregate(user, address, individual, country));

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getEmail()).isEqualTo("ivan.petrov@example.org");
        assertThat(response.getFirstName()).isEqualTo("Иван");
        assertThat(response.getLastName()).isEqualTo("Петров");
        assertThat(response.getFilled()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.getAddress()).isNotNull();
        assertThat(response.getAddress().getCountryAlpha3()).isEqualTo("RUS");
        assertThat(response.getAddress().getCountryAlpha2()).isEqualTo("RU");
        assertThat(response.getAddress().getCity()).isEqualTo("Moscow");
        assertThat(response.getAddress().getAddressLine()).isEqualTo("ул. Пример, д. 1");
        assertThat(response.getIndividual()).isNotNull();
        assertThat(response.getIndividual().getStatus()).isEqualTo(net.example.person.dto.IndividualStatus.NEW);
        assertThat(response.getIndividual().getVerifiedAt()).isNull();
        assertThat(response.getIndividual().getArchivedAt()).isNull();
    }

    @Test
    @DisplayName("незаполненный агрегат отдаёт null во вложенных объектах")
    void mapsUserWithoutNestedEntities() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("empty@example.org");
        user.setFirstName("A");
        user.setLastName("B");
        user.setFilled(false);
        user.setCreated(NOW);
        user.setUpdated(NOW);

        UserResponse response = mapper.toResponse(new UserAggregate(user, null, null, null));

        assertThat(response.getAddress()).isNull();
        assertThat(response.getIndividual()).isNull();
        assertThat(response.getFilled()).isFalse();
    }

    @Test
    @DisplayName("адрес без страны не падает и отдаёт null в кодах")
    void mapsAddressWithoutCountry() {
        AddressEntity address = address(null);

        AddressResponse response = mapper.toAddressResponse(address, null);

        assertThat(response.getCountryAlpha3()).isNull();
        assertThat(response.getCountryAlpha2()).isNull();
        assertThat(response.getState()).isEqualTo("Moscow");
        assertThat(response.getZipCode()).isEqualTo("101000");
    }

    @Test
    @DisplayName("индивидуальные данные без статуса не падают")
    void mapsIndividualWithoutStatus() {
        IndividualEntity individual = new IndividualEntity();
        individual.setId(UUID.randomUUID());

        IndividualResponse response = mapper.toIndividualResponse(individual);

        assertThat(response.getStatus()).isNull();
        assertThat(response.getPassportNumber()).isNull();
    }

    @Test
    @DisplayName("обновление пользователя меняет только переданные поля")
    void appliesOnlyProvidedUserFields() {
        UserEntity user = user(null, null);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setLastName("Петров-Старший");

        mapper.apply(user, request);

        assertThat(user.getLastName()).isEqualTo("Петров-Старший");
        assertThat(user.getFirstName()).isEqualTo("Иван");
    }

    @Test
    @DisplayName("обновление адреса меняет только переданные поля")
    void appliesOnlyProvidedAddressFields() {
        AddressEntity address = address(country("RUS", "RU"));
        UpdateAddressRequest request = new UpdateAddressRequest();
        request.setCity("Saint Petersburg");

        mapper.apply(address, request);

        assertThat(address.getCity()).isEqualTo("Saint Petersburg");
        assertThat(address.getAddress()).isEqualTo("ул. Пример, д. 1");
        assertThat(address.getZipCode()).isEqualTo("101000");
    }

    @Test
    @DisplayName("обновление индивидуальных данных меняет статус")
    void appliesIndividualStatus() {
        IndividualEntity individual = individual();
        UpdateIndividualRequest request = new UpdateIndividualRequest();
        request.setStatus(net.example.person.dto.IndividualStatus.VERIFIED);

        mapper.apply(individual, request);

        assertThat(individual.getStatus()).isEqualTo(IndividualStatus.VERIFIED);
        assertThat(individual.getPhoneNumber()).isEqualTo("+79991234567");
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

    private AddressEntity address(CountryEntity country) {
        AddressEntity address = AddressEntity.newInstance();
        address.setCountryId(country == null ? null : country.getId());
        address.setCity("Moscow");
        address.setState("Moscow");
        address.setZipCode("101000");
        address.setAddress("ул. Пример, д. 1");
        address.setCreated(NOW);
        address.setUpdated(NOW);
        return address;
    }

    private IndividualEntity individual() {
        IndividualEntity individual = IndividualEntity.newInstance();
        individual.setPassportNumber("1234 567890");
        individual.setPhoneNumber("+79991234567");
        return individual;
    }

    private UserEntity user(AddressEntity address, IndividualEntity individual) {
        UserEntity user = UserEntity.newInstance();
        user.setEmail("ivan.petrov@example.org");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setCreated(NOW);
        user.setUpdated(NOW);
        user.setAddressId(address == null ? null : address.getId());
        user.setFilled(address != null && individual != null);
        if (individual != null) {
            individual.setUserId(user.getId());
        }
        return user;
    }
}

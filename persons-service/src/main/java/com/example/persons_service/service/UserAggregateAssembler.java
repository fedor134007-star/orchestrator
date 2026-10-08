package com.example.persons_service.service;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import com.example.persons_service.exception.IncompleteAggregateUpdateException;
import com.example.persons_service.mapper.PersonDtoMapper;
import net.example.person.dto.CreateAddressRequest;
import net.example.person.dto.CreateIndividualRequest;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.UpdateAddressRequest;
import net.example.person.dto.UpdateIndividualRequest;
import net.example.person.dto.UpdateUserRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Собирает агрегат из запроса: создание «с нуля» и применение PATCH.
 *
 * <p>Здесь живёт вся политика агрегата: обязательность полей адреса, пересчёт {@code filled},
 * patch-семантика (непереданное поле не меняется) и определение того, что реально изменилось.
 * Сервисный слой после этого только оркестрирует шаги.</p>
 */
@Component
public class UserAggregateAssembler {

    private final CountryResolver countryResolver;
    private final PersonDtoMapper mapper;
    private final Clock clock;

    public UserAggregateAssembler(CountryResolver countryResolver, PersonDtoMapper mapper, Clock clock) {
        this.countryResolver = countryResolver;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Готовит новый агрегат к вставке (страна резолвится, если передан адрес). */
    public Mono<UserAggregate> forCreate(CreateUserRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (request.getAddress() == null) {
            return Mono.just(newAggregate(request, null, null, now));
        }
        return countryResolver.forCreate(request.getAddress())
                .map(country -> newAggregate(
                        request, country, newAddress(request.getAddress(), country, now), now));
    }

    /**
     * Применяет PATCH к загруженному агрегату.
     *
     * <p>Изменения применяются к копиям: состояние «до» остаётся нетронутым, поэтому его можно
     * использовать и для сравнения, и для аудита. Если ничего не изменилось, возвращается
     * патч с {@code changed = false} — ни записи, ни ревизии не будет, а {@code updatedAt}
     * не сдвигается на пустом запросе.</p>
     */
    public Mono<AggregatePatch> forUpdate(UserAggregate before, UpdateUserRequest request) {
        return countryResolver.forUpdate(before, request)
                .map(country -> applyPatch(before, request, country))
                .switchIfEmpty(Mono.fromSupplier(() -> applyPatch(before, request, null)));
    }

    // ------------------------------------------------------------------
    // Создание
    // ------------------------------------------------------------------

    private UserAggregate newAggregate(CreateUserRequest request,
                                       CountryEntity country,
                                       AddressEntity address,
                                       OffsetDateTime now) {
        IndividualEntity individual = request.getIndividual() == null
                ? null
                : newIndividual(request.getIndividual());

        UserEntity user = UserEntity.newInstance();
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setCreated(now);
        user.setUpdated(now);
        mapper.apply(user, request);
        user.setAddressId(address == null ? null : address.getId());
        user.setFilled(address != null && individual != null);

        if (individual != null) {
            individual.setUserId(user.getId());
        }
        return new UserAggregate(user, address, individual, country);
    }

    private AddressEntity newAddress(CreateAddressRequest request, CountryEntity country, OffsetDateTime now) {
        AddressEntity address = AddressEntity.newInstance();
        address.setCreated(now);
        address.setUpdated(now);
        address.setCountryId(country.getId());
        mapper.apply(address, request);
        return address;
    }

    private IndividualEntity newIndividual(CreateIndividualRequest request) {
        IndividualEntity individual = IndividualEntity.newInstance();
        mapper.apply(individual, request);
        return individual;
    }

    // ------------------------------------------------------------------
    // Изменение
    // ------------------------------------------------------------------

    private AggregatePatch applyPatch(UserAggregate before, UpdateUserRequest request, CountryEntity resolvedCountry) {
        OffsetDateTime now = OffsetDateTime.now(clock);

        UserEntity user = before.user().copy();
        AddressEntity address = before.address() == null ? null : before.address().copy();
        IndividualEntity individual = before.individual() == null ? null : before.individual().copy();

        if (request.getEmail() != null) {
            user.setEmail(normalizeEmail(request.getEmail()));
        }
        mapper.apply(user, request);

        // Адрес: создать, если его не было, затем применить только переданные поля.
        boolean addressCreated = false;
        boolean addressTouched = false;
        if (request.getAddress() != null) {
            UpdateAddressRequest addressRequest = request.getAddress();
            if (address == null) {
                address = AddressEntity.newInstance();
                address.setCreated(now);
                addressCreated = true;
            }
            // снимок берётся до применения страны и полей, иначе смена страны не была бы замечена
            AddressEntity addressBeforePatch = address.copy();
            if (resolvedCountry != null) {
                address.setCountryId(resolvedCountry.getId());
            }
            mapper.apply(address, addressRequest);
            requireCompleteAddress(address);
            addressTouched = addressCreated || addressDiffers(addressBeforePatch, address);
            if (addressTouched) {
                address.setUpdated(now);
                user.setAddressId(address.getId());
            }
        }

        // Индивидуальные данные: создать, если их не было, затем применить переданные поля.
        boolean individualCreated = false;
        boolean individualTouched = false;
        if (request.getIndividual() != null) {
            UpdateIndividualRequest individualRequest = request.getIndividual();
            if (individual == null) {
                individual = IndividualEntity.newInstance();
                individual.setUserId(user.getId());
                individualCreated = true;
            }
            IndividualEntity individualBeforePatch = individual.copy();
            mapper.apply(individual, individualRequest);
            individualTouched = individualCreated || individualDiffers(individualBeforePatch, individual);
        }

        user.setFilled(address != null && individual != null);

        boolean userTouched = userFieldsDiffer(before.user(), user);
        if (!userTouched && !addressTouched && !individualTouched) {
            return new AggregatePatch(before, before, false, null, null);
        }

        user.setUpdated(now);
        CountryEntity country = resolvedCountry != null ? resolvedCountry : before.country();

        return new AggregatePatch(
                before,
                new UserAggregate(user, address, individual, country),
                true,
                new AggregatePatch.AddressChange(address, addressCreated, addressTouched),
                new AggregatePatch.IndividualChange(individual, individualCreated, individualTouched));
    }

    // ------------------------------------------------------------------
    // Политики и сравнения
    // ------------------------------------------------------------------

    private void requireCompleteAddress(AddressEntity address) {
        if (address.getCity() == null || address.getAddress() == null || address.getCountryId() == null) {
            throw new IncompleteAggregateUpdateException(
                    "Адрес должен содержать city, addressLine и код страны");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim();
    }

    private boolean userFieldsDiffer(UserEntity before, UserEntity after) {
        return !Objects.equals(before.getEmail(), after.getEmail())
                || !Objects.equals(before.getFirstName(), after.getFirstName())
                || !Objects.equals(before.getLastName(), after.getLastName())
                || before.isFilled() != after.isFilled()
                || !Objects.equals(before.getAddressId(), after.getAddressId());
    }

    private boolean addressDiffers(AddressEntity before, AddressEntity after) {
        return !Objects.equals(before.getCity(), after.getCity())
                || !Objects.equals(before.getState(), after.getState())
                || !Objects.equals(before.getZipCode(), after.getZipCode())
                || !Objects.equals(before.getAddress(), after.getAddress())
                || !Objects.equals(before.getCountryId(), after.getCountryId());
    }

    private boolean individualDiffers(IndividualEntity before, IndividualEntity after) {
        return !Objects.equals(before.getPassportNumber(), after.getPassportNumber())
                || !Objects.equals(before.getPhoneNumber(), after.getPhoneNumber())
                || before.getStatus() != after.getStatus()
                || !Objects.equals(before.getVerifiedAt(), after.getVerifiedAt())
                || !Objects.equals(before.getArchivedAt(), after.getArchivedAt());
    }
}

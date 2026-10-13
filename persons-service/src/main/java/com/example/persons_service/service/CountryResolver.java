package com.example.persons_service.service;

import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.exception.IncompleteAggregateUpdateException;
import com.example.persons_service.exception.UnknownCountryException;
import com.example.persons_service.repository.CountryRepository;
import net.example.person.dto.CreateAddressRequest;
import net.example.person.dto.UpdateAddressRequest;
import net.example.person.dto.UpdateUserRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Доменная политика по странам адреса: справочник, согласованность кодов alpha-2/alpha-3
 * и требование обязательного кода при создании адреса.
 */
@Component
public class CountryResolver {

    private final CountryRepository countryRepository;

    public CountryResolver(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    /** Страна нового адреса: если адрес передан, страна обязательна. */
    public Mono<CountryEntity> forCreate(CreateAddressRequest address) {
        if (address == null) {
            return Mono.empty();
        }
        return resolve(address.getCountryAlpha3(), address.getCountryAlpha2());
    }

    /**
     * Страна для PATCH. Пустой результат означает «оставить текущую»: адрес не затрагивается
     * либо коды не переданы, а адрес уже существует. Создание адреса без кода страны —
     * неполный агрегат.
     */
    public Mono<CountryEntity> forUpdate(UserAggregate before, UpdateUserRequest request) {
        if (request.getAddress() == null) {
            return Mono.empty();
        }
        UpdateAddressRequest addressRequest = request.getAddress();
        if (addressRequest.getCountryAlpha3() != null || addressRequest.getCountryAlpha2() != null) {
            return resolve(addressRequest.getCountryAlpha3(), addressRequest.getCountryAlpha2());
        }
        if (before.address() == null) {
            return Mono.error(new IncompleteAggregateUpdateException(
                    "Адрес должен содержать city, addressLine и код страны"));
        }
        return Mono.empty();
    }

    private Mono<CountryEntity> resolve(String alpha3, String alpha2) {
        if (alpha3 == null && alpha2 == null) {
            return Mono.error(new IncompleteAggregateUpdateException("Не указан код страны адреса"));
        }

        Mono<CountryEntity> lookup = alpha3 != null
                ? countryRepository.findByAlpha3IgnoreCase(alpha3)
                .switchIfEmpty(Mono.error(new UnknownCountryException(
                        "Страна с кодом alpha-3 " + alpha3 + " не найдена в справочнике")))
                : countryRepository.findByAlpha2IgnoreCase(alpha2)
                .switchIfEmpty(Mono.error(new UnknownCountryException(
                        "Страна с кодом alpha-2 " + alpha2 + " не найдена в справочнике")));

        return lookup.flatMap(country -> {
            if (alpha2 != null && !country.getAlpha2().equalsIgnoreCase(alpha2)) {
                return Mono.error(new UnknownCountryException(
                        "Коды страны противоречат друг другу: alpha-3=" + alpha3 + ", alpha-2=" + alpha2));
            }
            return Mono.just(country);
        });
    }
}

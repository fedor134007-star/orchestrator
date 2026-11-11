package com.example.persons_service.repository;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import com.example.persons_service.exception.UserNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Агрегатный репозиторий: чтение и запись агрегата целиком поверх трёх JPA-таблиц.
 *
 * <p>Связи хранятся идентификаторами, поэтому здесь сосредоточены две вещи, которых не
 * должно быть в сценариях: порядок обращения к таблицам (адрес → пользователь →
 * индивидуальные данные из-за внешних ключей) и «может отсутствовать» для вложенных
 * сущностей. Все операции выполняются внутри транзакции сервисного слоя.</p>
 */
@Component
public class UserAggregateStore {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final IndividualRepository individualRepository;
    private final CountryRepository countryRepository;

    public UserAggregateStore(UserRepository userRepository,
                              AddressRepository addressRepository,
                              IndividualRepository individualRepository,
                              CountryRepository countryRepository) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.individualRepository = individualRepository;
        this.countryRepository = countryRepository;
    }

    public UserAggregate loadById(UUID id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return assemble(user);
    }

    public UserAggregate loadByEmail(String email) {
        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        return assemble(user);
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    /** Вставка нового агрегата: адрес (на него ссылается users.address_id), затем пользователь. */
    public UserAggregate insert(UserAggregate aggregate) {
        AddressEntity savedAddress = aggregate.address() == null
                ? null
                : addressRepository.save(aggregate.address());

        UserEntity savedUser = userRepository.save(aggregate.user());

        IndividualEntity savedIndividual = aggregate.individual() == null
                ? null
                : individualRepository.save(aggregate.individual());

        return new UserAggregate(savedUser, savedAddress, savedIndividual, aggregate.country());
    }

    /** Запись изменений патча: пишем только то, что реально затронуто. */
    public UserAggregate update(AggregatePatch patch) {
        UserAggregate after = patch.after();

        if (isAddressTouched(patch)) {
            addressRepository.save(patch.address().entity());
        }
        UserEntity savedUser = userRepository.save(after.user());
        if (isIndividualTouched(patch)) {
            individualRepository.save(patch.individual().entity());
        }
        return new UserAggregate(savedUser, after.address(), after.individual(), after.country());
    }

    /** Удаление агрегата: сначала снимаем внешние ключи, затем корень. */
    public void delete(UserAggregate aggregate) {
        if (aggregate.individual() != null) {
            individualRepository.delete(aggregate.individual());
        }
        userRepository.delete(aggregate.user());
        if (aggregate.address() != null) {
            addressRepository.delete(aggregate.address());
        }
    }

    private boolean isAddressTouched(AggregatePatch patch) {
        return patch.address() != null && patch.address().touched();
    }

    private boolean isIndividualTouched(AggregatePatch patch) {
        return patch.individual() != null && patch.individual().touched();
    }

    private UserAggregate assemble(UserEntity user) {
        AddressEntity address = user.getAddressId() == null
                ? null
                : addressRepository.findById(user.getAddressId()).orElse(null);

        IndividualEntity individual = individualRepository.findByUserId(user.getId()).orElse(null);

        CountryEntity country = null;
        if (address != null && address.getCountryId() != null) {
            country = countryRepository.findById(address.getCountryId()).orElse(null);
        }
        return new UserAggregate(user, address, individual, country);
    }
}

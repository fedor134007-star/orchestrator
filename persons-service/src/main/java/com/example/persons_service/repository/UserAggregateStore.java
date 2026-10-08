package com.example.persons_service.repository;

import com.example.persons_service.aggregate.AggregatePatch;
import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import com.example.persons_service.exception.UserNotFoundException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Агрегатный репозиторий: чтение и запись агрегата целиком поверх трёх R2DBC-таблиц.
 *
 * <p>Связей в R2DBC нет, поэтому здесь сосредоточены две вещи, которых не должно быть
 * в сценариях: порядок обращения к таблицам (адрес → пользователь → индивидуальные данные
 * из-за внешних ключей) и «может отсутствовать» для вложенных сущностей.</p>
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

    public Mono<UserAggregate> loadById(UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)))
                .flatMap(this::assemble);
    }

    public Mono<UserAggregate> loadByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .switchIfEmpty(Mono.error(new UserNotFoundException(email)))
                .flatMap(this::assemble);
    }

    public Mono<Boolean> emailExists(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    /** Вставка нового агрегата: адрес (на него ссылается users.address_id), затем пользователь. */
    public Mono<UserAggregate> insert(UserAggregate aggregate) {
        return saveAddress(aggregate.address())
                .flatMap(savedAddress -> userRepository.save(aggregate.user())
                        .flatMap(savedUser -> saveIndividual(aggregate.individual())
                                .map(savedIndividual -> new UserAggregate(
                                        savedUser,
                                        savedAddress.orElse(null),
                                        savedIndividual.orElse(null),
                                        aggregate.country()))));
    }

    /** Запись изменений патча: пишем только то, что реально затронуто. */
    public Mono<UserAggregate> update(AggregatePatch patch) {
        UserAggregate after = patch.after();

        Mono<Void> addressStep = isAddressTouched(patch)
                ? addressRepository.save(patch.address().entity()).then()
                : Mono.empty();

        Mono<Void> individualStep = isIndividualTouched(patch)
                ? individualRepository.save(patch.individual().entity()).then()
                : Mono.empty();

        return addressStep
                .then(userRepository.save(after.user()))
                .flatMap(savedUser -> individualStep.thenReturn(savedUser))
                .map(savedUser -> new UserAggregate(
                        savedUser, after.address(), after.individual(), after.country()));
    }

    /** Удаление агрегата: сначала снимаем внешние ключи, затем корень. */
    public Mono<Void> delete(UserAggregate aggregate) {
        Mono<Void> deleteIndividual = aggregate.individual() == null
                ? Mono.empty()
                : individualRepository.delete(aggregate.individual());
        Mono<Void> deleteAddress = aggregate.address() == null
                ? Mono.empty()
                : addressRepository.delete(aggregate.address());

        return deleteIndividual
                .then(userRepository.delete(aggregate.user()))
                .then(deleteAddress);
    }

    private boolean isAddressTouched(AggregatePatch patch) {
        return patch.address() != null && patch.address().touched();
    }

    private boolean isIndividualTouched(AggregatePatch patch) {
        return patch.individual() != null && patch.individual().touched();
    }

    private Mono<UserAggregate> assemble(UserEntity user) {
        Mono<Optional<AddressEntity>> address = user.getAddressId() == null
                ? Mono.just(Optional.empty())
                : addressRepository.findById(user.getAddressId()).map(Optional::of).defaultIfEmpty(Optional.empty());

        Mono<Optional<IndividualEntity>> individual = individualRepository.findByUserId(user.getId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(address, individual).flatMap(tuple -> {
            AddressEntity foundAddress = tuple.getT1().orElse(null);
            IndividualEntity foundIndividual = tuple.getT2().orElse(null);

            if (foundAddress == null || foundAddress.getCountryId() == null) {
                return Mono.just(new UserAggregate(user, foundAddress, foundIndividual, null));
            }
            return countryRepository.findById(foundAddress.getCountryId())
                    .map(country -> new UserAggregate(user, foundAddress, foundIndividual, country))
                    .defaultIfEmpty(new UserAggregate(user, foundAddress, foundIndividual, null));
        });
    }

    private Mono<Optional<AddressEntity>> saveAddress(AddressEntity address) {
        return address == null
                ? Mono.just(Optional.empty())
                : addressRepository.save(address).map(Optional::of);
    }

    private Mono<Optional<IndividualEntity>> saveIndividual(IndividualEntity individual) {
        return individual == null
                ? Mono.just(Optional.empty())
                : individualRepository.save(individual).map(Optional::of);
    }
}

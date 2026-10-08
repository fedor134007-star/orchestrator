package com.example.persons_service.repository;

import com.example.persons_service.entity.CountryEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CountryRepository extends ReactiveCrudRepository<CountryEntity, Integer> {

    @Query("SELECT * FROM person.countries WHERE lower(alpha3) = lower(:alpha3)")
    Mono<CountryEntity> findByAlpha3IgnoreCase(@Param("alpha3") String alpha3);

    @Query("SELECT * FROM person.countries WHERE lower(alpha2) = lower(:alpha2)")
    Mono<CountryEntity> findByAlpha2IgnoreCase(@Param("alpha2") String alpha2);
}

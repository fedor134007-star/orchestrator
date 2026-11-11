package com.example.persons_service.repository;

import com.example.persons_service.entity.CountryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CountryRepository extends JpaRepository<CountryEntity, Integer> {

    @Query(value = "SELECT * FROM person.countries WHERE lower(alpha3) = lower(:alpha3)", nativeQuery = true)
    Optional<CountryEntity> findByAlpha3IgnoreCase(@Param("alpha3") String alpha3);

    @Query(value = "SELECT * FROM person.countries WHERE lower(alpha2) = lower(:alpha2)", nativeQuery = true)
    Optional<CountryEntity> findByAlpha2IgnoreCase(@Param("alpha2") String alpha2);
}

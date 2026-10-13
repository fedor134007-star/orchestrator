package com.example.persons_service.repository;

import com.example.persons_service.entity.IndividualEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IndividualRepository extends JpaRepository<IndividualEntity, UUID> {

    @Query(value = "SELECT * FROM person.individuals WHERE user_id = :userId", nativeQuery = true)
    Optional<IndividualEntity> findByUserId(@Param("userId") UUID userId);
}

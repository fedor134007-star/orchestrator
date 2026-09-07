package com.example.persons_service.repository;

import com.example.persons_service.entity.IndividualEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface IndividualRepository extends ReactiveCrudRepository<IndividualEntity, UUID> {

    @Query("SELECT * FROM person.individuals WHERE user_id = :userId")
    Mono<IndividualEntity> findByUserId(@Param("userId") UUID userId);
}

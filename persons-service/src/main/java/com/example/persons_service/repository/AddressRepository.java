package com.example.persons_service.repository;

import com.example.persons_service.entity.AddressEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface AddressRepository extends ReactiveCrudRepository<AddressEntity, UUID> {
}

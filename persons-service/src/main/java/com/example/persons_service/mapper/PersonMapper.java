package com.example.persons_service.mapper;

import com.example.persons_service.entity.Person;
import net.generated.person.dto.PersonRegistrationRequest;
import net.generated.person.dto.PersonResponse;
import net.generated.person.dto.PersonUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PersonMapper {

    @Mapping(target = "userUid", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Person toEntity(PersonRegistrationRequest request);

    PersonResponse toResponse(Person person);

    @Mapping(target = "userUid", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(PersonUpdateRequest request, @MappingTarget Person person);
}
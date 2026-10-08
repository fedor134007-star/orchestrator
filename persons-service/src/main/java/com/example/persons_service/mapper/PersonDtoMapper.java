package com.example.persons_service.mapper;

import com.example.persons_service.aggregate.UserAggregate;
import com.example.persons_service.entity.AddressEntity;
import com.example.persons_service.entity.CountryEntity;
import com.example.persons_service.entity.IndividualEntity;
import com.example.persons_service.entity.UserEntity;
import net.example.person.dto.AddressResponse;
import net.example.person.dto.CreateAddressRequest;
import net.example.person.dto.CreateIndividualRequest;
import net.example.person.dto.CreateUserRequest;
import net.example.person.dto.IndividualResponse;
import net.example.person.dto.UpdateAddressRequest;
import net.example.person.dto.UpdateIndividualRequest;
import net.example.person.dto.UpdateUserRequest;
import net.example.person.dto.UserResponse;
import org.springframework.stereotype.Component;

/**
 * Явное отображение между сущностями и DTO контракта.
 *
 * <p>Сущности наружу не отдаются: контракт не связан с моделью хранения, а ленивой
 * загрузки в R2DBC нет — агрегат собирается сервисным слоем.</p>
 *
 * <p>Методы обновления реализуют patch-семантику: {@code null} означает «поле не передано»
 * и оставляет текущее значение без изменений.</p>
 */
@Component
public class PersonDtoMapper {

    // ---------------------------------------------------------------
    // Entity → DTO
    // ---------------------------------------------------------------

    public UserResponse toResponse(UserAggregate aggregate) {
        UserEntity user = aggregate.user();

        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setFilled(user.isFilled());
        response.setCreatedAt(user.getCreated());
        response.setUpdatedAt(user.getUpdated());
        if (aggregate.address() != null) {
            response.setAddress(toAddressResponse(aggregate.address(), aggregate.country()));
        }
        if (aggregate.individual() != null) {
            response.setIndividual(toIndividualResponse(aggregate.individual()));
        }
        return response;
    }

    public AddressResponse toAddressResponse(AddressEntity address, CountryEntity country) {
        AddressResponse response = new AddressResponse();
        response.setId(address.getId());
        if (country != null) {
            response.setCountryAlpha3(country.getAlpha3());
            response.setCountryAlpha2(country.getAlpha2());
        }
        response.setCity(address.getCity());
        response.setState(address.getState());
        response.setZipCode(address.getZipCode());
        response.setAddressLine(address.getAddress());
        return response;
    }

    public IndividualResponse toIndividualResponse(IndividualEntity individual) {
        IndividualResponse response = new IndividualResponse();
        response.setId(individual.getId());
        response.setPassportNumber(individual.getPassportNumber());
        response.setPhoneNumber(individual.getPhoneNumber());
        if (individual.getStatus() != null) {
            response.setStatus(net.example.person.dto.IndividualStatus.fromValue(individual.getStatus().name()));
        }
        response.setVerifiedAt(individual.getVerifiedAt());
        response.setArchivedAt(individual.getArchivedAt());
        return response;
    }

    // ---------------------------------------------------------------
    // Запросы → Entity
    // ---------------------------------------------------------------

    public void apply(UserEntity target, CreateUserRequest request) {
        target.setFirstName(request.getFirstName());
        target.setLastName(request.getLastName());
    }

    public void apply(UserEntity target, UpdateUserRequest request) {
        if (request.getFirstName() != null) {
            target.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            target.setLastName(request.getLastName());
        }
    }

    public void apply(AddressEntity target, CreateAddressRequest request) {
        target.setCity(request.getCity());
        target.setState(request.getState());
        target.setZipCode(request.getZipCode());
        target.setAddress(request.getAddressLine());
    }

    public void apply(AddressEntity target, UpdateAddressRequest request) {
        if (request.getCity() != null) {
            target.setCity(request.getCity());
        }
        if (request.getState() != null) {
            target.setState(request.getState());
        }
        if (request.getZipCode() != null) {
            target.setZipCode(request.getZipCode());
        }
        if (request.getAddressLine() != null) {
            target.setAddress(request.getAddressLine());
        }
    }

    public void apply(IndividualEntity target, CreateIndividualRequest request) {
        target.setPassportNumber(request.getPassportNumber());
        target.setPhoneNumber(request.getPhoneNumber());
    }

    public void apply(IndividualEntity target, UpdateIndividualRequest request) {
        if (request.getPassportNumber() != null) {
            target.setPassportNumber(request.getPassportNumber());
        }
        if (request.getPhoneNumber() != null) {
            target.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getStatus() != null) {
            target.setStatus(com.example.persons_service.entity.IndividualStatus.valueOf(request.getStatus().name()));
        }
    }
}

package com.example.individuals_api.dto;

import net.generated.individuals.dto.RegistrationRequest;

public record KeycloakUserRequest(
        String email,
        String firstName,
        String lastName,
        boolean enabled,
        boolean emailVerified,
        String username
) {
    public static KeycloakUserRequest from(RegistrationRequest request) {
        return new KeycloakUserRequest(
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                true,
                true,
                request.getEmail()
        );
    }
}
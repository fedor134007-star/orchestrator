package com.example.individuals_api.service;

import net.generated.individualls.dto.CurrentUserResponse;
import net.generated.individualls.dto.RegistrationRequest;
import net.generated.individualls.dto.TokenResponse;
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<TokenResponse> register(RegistrationRequest user);

    Mono<CurrentUserResponse> getCurrentUser(String userId);

}

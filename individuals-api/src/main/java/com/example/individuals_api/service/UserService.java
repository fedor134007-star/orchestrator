package com.example.individuals_api.service;

import net.generated.individuals.dto.CurrentUserResponse;
import net.generated.individuals.dto.RegistrationRequest;
import net.generated.individuals.dto.TokenResponse;
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<TokenResponse> register(RegistrationRequest user);

    Mono<CurrentUserResponse> getCurrentUser(String userId);

}

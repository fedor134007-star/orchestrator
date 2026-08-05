package com.example.individuals_api.client;

import net.generated.individualls.dto.*;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface KeycloakClient {

    Mono<String> register(RegistrationRequest registrationRequest);

    Mono<CurrentUserResponse> getCurrentUser(String userId);


    Mono<TokenResponse> login(LoginRequest request);

    Mono<TokenResponse> refreshToken(RefreshTokenRequest refreshTokenRequest);
}

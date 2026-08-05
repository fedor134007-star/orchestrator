package com.example.individuals_api.service;

import net.generated.individuals.dto.LoginRequest;
import net.generated.individuals.dto.RefreshTokenRequest;
import net.generated.individuals.dto.TokenResponse;
import reactor.core.publisher.Mono;

public interface TokenService {
    Mono<TokenResponse> login(LoginRequest request);


    Mono<TokenResponse> refresh(RefreshTokenRequest request);
}

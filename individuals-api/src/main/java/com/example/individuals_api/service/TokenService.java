package com.example.individuals_api.service;

import net.generated.individualls.dto.LoginRequest;
import net.generated.individualls.dto.RefreshTokenRequest;
import net.generated.individualls.dto.TokenResponse;
import reactor.core.publisher.Mono;

public interface TokenService {
    Mono<TokenResponse> login(LoginRequest request);


    Mono<TokenResponse> refresh(RefreshTokenRequest request);
}

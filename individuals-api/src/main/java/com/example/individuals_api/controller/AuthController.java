package com.example.individuals_api.controller;

import com.example.individuals_api.service.TokenService;
import com.example.individuals_api.service.UserService;
import lombok.RequiredArgsConstructor;
import net.generated.individuals.api.AuthApi;
import net.generated.individuals.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final UserService userService;
    private final TokenService tokenService;

    @Override
    public Mono<ResponseEntity<TokenResponse>> login(Mono<LoginRequest> loginRequest, ServerWebExchange exchange) {
        return loginRequest
                .flatMap(tokenService::login)
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> refreshToken(Mono<RefreshTokenRequest> refreshTokenRequest, ServerWebExchange exchange) {
        return refreshTokenRequest
                .flatMap(tokenService::refresh)
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<TokenResponse>> register(Mono<RegistrationRequest> request, ServerWebExchange exchange) {
        return request
                .flatMap(userService::register)
                .map(ResponseEntity::ok);
    }

    @Override
    public Mono<ResponseEntity<CurrentUserResponse>> getCurrentUser(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .cast(Jwt.class)
                .map(jwt -> jwt.getClaimAsString("sub"))
                .flatMap(userService::getCurrentUser)
                .map(ResponseEntity::ok);
    }
}
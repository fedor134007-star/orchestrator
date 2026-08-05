package com.example.individuals_api.controller;

import com.example.individuals_api.service.TokenService;
import com.example.individuals_api.service.UserService;
import lombok.RequiredArgsConstructor;
import net.generated.individualls.dto.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;

    @PostMapping("/login")
    public Mono<TokenResponse> login(@RequestBody LoginRequest request) {
        return tokenService.login(request);
    }

    @PostMapping("/refresh")
    public Mono<TokenResponse> refreshToken(@RequestBody RefreshTokenRequest request) {
        return tokenService.refresh(request);
    }

    @PostMapping("/registration")
    public Mono<TokenResponse> register(
            @RequestBody RegistrationRequest request) {
        return userService.register(request);
    }

    @GetMapping("/me")
    public Mono<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.getCurrentUser(jwt.getClaimAsString("sub"));
    }
}
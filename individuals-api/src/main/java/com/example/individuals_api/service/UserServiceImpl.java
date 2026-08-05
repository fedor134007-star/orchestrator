package com.example.individuals_api.service;


import com.example.individuals_api.client.KeycloakClient;
import com.example.individuals_api.utils.AuthMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.generated.individuals.dto.CurrentUserResponse;
import net.generated.individuals.dto.LoginRequest;
import net.generated.individuals.dto.RegistrationRequest;
import net.generated.individuals.dto.TokenResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final KeycloakClient keycloakClient;
    private final TokenService tokenService;
    private final AuthMetrics metrics;


    public Mono<TokenResponse> register(RegistrationRequest request) {
        long start = System.currentTimeMillis();
        log.info("Register user: {}", request.getEmail());
        return keycloakClient.register(request)
                .flatMap(userId -> tokenService.login(new LoginRequest(request.getEmail(), request.getPassword())))
                .doOnSuccess(r -> metrics.recordRegistration(true, System.currentTimeMillis() - start))
                .doOnError(e -> metrics.recordRegistration(false, System.currentTimeMillis() - start));
    }


    public Mono<CurrentUserResponse> getCurrentUser(String userId) {
        log.info("Get Current User: {}", userId);
        return keycloakClient.getCurrentUser(userId);
    }


}

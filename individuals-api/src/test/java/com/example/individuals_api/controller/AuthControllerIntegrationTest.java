package com.example.individuals_api.controller;

import net.generated.individuals.dto.*;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private static String testEmail = "integration-" + System.currentTimeMillis() + "@test.com";
    private static String testPassword = "Test1234!";
    private static String accessToken;
    private static String refreshToken;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @Order(1)
    void register_shouldReturnTokens() {
        RegistrationRequest req = new RegistrationRequest();
        req.setEmail(testEmail);
        req.setPassword(testPassword);
        req.setConfirmPassword(testPassword);
        req.setFirstName("Integration");
        req.setLastName("Test");

        client().post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(req), RegistrationRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .value(r -> {
                    assertThat(r.getAccessToken()).isNotBlank();
                    assertThat(r.getRefreshToken()).isNotBlank();
                    accessToken = r.getAccessToken();
                    refreshToken = r.getRefreshToken();
                });
    }

    @Test
    @Order(2)
    void login_shouldReturnTokens() {
        LoginRequest req = new LoginRequest();
        req.setEmail(testEmail);
        req.setPassword(testPassword);

        client().post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(req), LoginRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .value(r -> assertThat(r.getAccessToken()).isNotBlank());
    }

    @Test
    @Order(3)
    void refresh_shouldReturnNewTokens() {
        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken(refreshToken);

        client().post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(req), RefreshTokenRequest.class)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class)
                .value(r -> assertThat(r.getAccessToken()).isNotBlank());
    }
}
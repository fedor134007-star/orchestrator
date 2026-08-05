package com.example.individuals_api.client;

import com.example.individuals_api.config.AdminTokenProvider;
import com.example.individuals_api.config.KeycloakProperties;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import net.generated.individuals.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KeycloakClientImplIntegrationTest {

    private static final String REALM = "individual";
    private static final String CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "test-secret";

    static KeycloakContainer keycloak = new KeycloakContainer()
            .withRealmImportFile("test-realm.json")
            .withAdminUsername("admin")
            .withAdminPassword("admin");

    private KeycloakClientImpl keycloakClient;
    private WebClient keycloakWebClient;
    private KeycloakProperties properties;
    private AdminTokenProvider tokenProvider;

    @BeforeAll
    static void startContainer() {
        keycloak.start();
    }

    @AfterAll
    static void stopContainer() {
        keycloak.stop();
    }

    @BeforeEach
    void setUp() {
        String authServerUrl = keycloak.getAuthServerUrl();

        keycloakWebClient = WebClient.builder()
                .baseUrl(authServerUrl)
                .build();

        properties = new KeycloakProperties();
        properties.setRealm(REALM);
        properties.setClientId(CLIENT_ID);
        properties.setClientSecret(CLIENT_SECRET);

        tokenProvider = new AdminTokenProvider(
                keycloakWebClient,
                CLIENT_ID,
                CLIENT_SECRET,
                REALM
        );

        keycloakClient = new KeycloakClientImpl(tokenProvider, keycloakWebClient, properties);
    }

    @Test
    @DisplayName("Должен успешно зарегистрировать пользователя и вернуть ID")
    void shouldRegisterUserSuccessfully() {
        // given
        RegistrationRequest request = createRegistrationRequest("register@test.com");

        // when
        var resultMono = keycloakClient.register(request);

        // then
        StepVerifier.create(resultMono)
                .assertNext(userId -> {
                    assertThat(userId).isNotBlank();
                    assertThat(userId).matches("[a-f0-9\\-]{36}");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Должен успешно залогиниться и получить токены")
    void shouldLoginSuccessfully() {
        // given
        RegistrationRequest regRequest = createRegistrationRequest("login@test.com");
        keycloakClient.register(regRequest).block(Duration.ofSeconds(10));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(regRequest.getEmail());
        loginRequest.setPassword(regRequest.getPassword());

        // when
        var resultMono = keycloakClient.login(loginRequest);

        // then
        StepVerifier.create(resultMono)
                .assertNext(tokenResponse -> {
                    assertThat(tokenResponse.getAccessToken()).isNotBlank();
                    assertThat(tokenResponse.getRefreshToken()).isNotBlank();
                    assertThat(tokenResponse.getExpiresIn()).isPositive();
                    assertThat(tokenResponse.getTokenType()).isEqualTo("Bearer");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Refresh token")
    void shouldRefreshTokenSuccessfully() {
        // given
        RegistrationRequest regRequest = createRegistrationRequest("refresh@test.com");
        keycloakClient.register(regRequest).block(Duration.ofSeconds(10));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(regRequest.getEmail());
        loginRequest.setPassword(regRequest.getPassword());

        TokenResponse loginResponse = keycloakClient.login(loginRequest).block(Duration.ofSeconds(10));

        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(loginResponse.getRefreshToken());

        // when
        var resultMono = keycloakClient.refreshToken(refreshRequest);

        // then
        StepVerifier.create(resultMono)
                .assertNext(tokenResponse -> {
                    assertThat(tokenResponse.getAccessToken()).isNotBlank();
                    assertThat(tokenResponse.getRefreshToken()).isNotBlank();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Get current user")
    void shouldGetCurrentUserSuccessfully() {
        // given
        RegistrationRequest regRequest = createRegistrationRequest("current@test.com");
        String userId = keycloakClient.register(regRequest).block(Duration.ofSeconds(10));

        // when
        var resultMono = keycloakClient.getCurrentUser(userId);

        // then
        StepVerifier.create(resultMono)
                .assertNext(user -> {
                    assertThat(user.getEmail()).isEqualTo(regRequest.getEmail());
                    assertThat(user.getFirstName()).isEqualTo(regRequest.getFirstName());
                    assertThat(user.getLastName()).isEqualTo(regRequest.getLastName());
                    assertThat(user.getStatus()).isEqualTo(CurrentUserResponse.StatusEnum.ACTIVE);
                    assertThat(user.getId()).isEqualTo(UUID.fromString(userId));
                    assertThat(user.getCreatedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Error login")
    void shouldFailLoginWithInvalidCredentials() {
        // given
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("nonexistent@test.com");
        loginRequest.setPassword("wrong-password");

        // when
        var resultMono = keycloakClient.login(loginRequest);

        // then
        StepVerifier.create(resultMono)
                .expectError()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Full registration")
    void shouldPerformFullUserLifecycle() {
        // given
        RegistrationRequest regRequest = createRegistrationRequest("lifecycle@test.com");

        // Registration
        String userId = keycloakClient.register(regRequest).block(Duration.ofSeconds(10));
        assertThat(userId).isNotBlank();

        // Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail(regRequest.getEmail());
        loginRequest.setPassword(regRequest.getPassword());

        TokenResponse tokens = keycloakClient.login(loginRequest).block(Duration.ofSeconds(10));
        assertThat(tokens.getAccessToken()).isNotBlank();

        // Get profile
        CurrentUserResponse user = keycloakClient.getCurrentUser(userId).block(Duration.ofSeconds(10));
        assertThat(user.getEmail()).isEqualTo(regRequest.getEmail());

        // Refresh token
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest();
        refreshRequest.setRefreshToken(tokens.getRefreshToken());

        TokenResponse refreshedTokens = keycloakClient.refreshToken(refreshRequest).block(Duration.ofSeconds(10));
        assertThat(refreshedTokens.getAccessToken()).isNotEqualTo(tokens.getAccessToken());
    }

    private RegistrationRequest createRegistrationRequest(String email) {
        RegistrationRequest request = new RegistrationRequest();
        request.setEmail(email);
        request.setFirstName("Test");
        request.setLastName("User");
        request.setPassword("Test123!");
        return request;
    }
}
package com.example.persons_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные проверки на реальном PostgreSQL (Testcontainers) для реактивного стека:
 * миграции Flyway, R2DBC-репозитории, транзакционные границы агрегата, собственный аудит,
 * RFC 9457 и actuator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.tracing.enabled=false",
        "logging.level.root=WARN",
        "logging.level.com.example.persons_service=INFO"
})
@AutoConfigureWebTestClient
@DisplayName("person-service (WebFlux + R2DBC): интеграционные сценарии")
class PersonServiceIntegrationTest {

    /**
     * Контейнер поднимается в статическом блоке: контекст Spring создаётся раньше
     * beforeAll-колбэков JUnit, поэтому ленивого старта здесь недостаточно.
     */
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        // Рабочее подключение приложения — реактивное
        registry.add("spring.r2dbc.url",
                () -> "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                        + "/" + POSTGRES.getDatabaseName());
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);

        // JDBC-подключение нужно Flyway
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    private JdbcTemplate jdbcTemplate;

    @Autowired
    void setUpJdbcTemplate(DataSource dataSource) {
        // В Boot 4 JdbcTemplate не создаётся автоматически: собираем его из DataSource,
        // который нужен Flyway (JDBC-подключение).
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("создание агрегата, чтение по id и по email без учёта регистра")
    void createsAndReadsAggregate() {
        String email = uniqueEmail();

        byte[] rawBody = webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(email))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.email").isEqualTo(email)
                .jsonPath("$.firstName").isEqualTo("Иван")
                .jsonPath("$.filled").isEqualTo(true)
                .jsonPath("$.address.countryAlpha3").isEqualTo("RUS")
                .jsonPath("$.individual.status").isEqualTo("NEW")
                .returnResult()
                .getResponseBody();

        String created = new String(rawBody, StandardCharsets.UTF_8);

        String id = objectMapper.readTree(created).get("id").asText();

        // У нового пользователя даты верификации и архивации естественно отсутствуют
        assertThat(objectMapper.readTree(created).get("individual").get("verifiedAt").isNull()).isTrue();
        assertThat(objectMapper.readTree(created).get("individual").get("archivedAt").isNull()).isTrue();

        webTestClient.get()
                .uri("/api/v1/users/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.address.addressLine").isEqualTo("ул. Пример, д. 1");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/users/by-email")
                        .queryParam("email", email.toUpperCase())
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id);
    }

    @Test
    @DisplayName("повторный email → 409 в формате RFC 9457")
    void rejectsDuplicateEmail() {
        String email = uniqueEmail();
        createUser(email);

        webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(email))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://example.org/problems/email-already-exists")
                .jsonPath("$.title").isEqualTo("Конфликт данных")
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.detail").isEqualTo("Пользователь с таким email уже существует")
                .jsonPath("$.instance").isEqualTo("/api/v1/users");
    }

    @Test
    @DisplayName("валидация запроса → 400 с детализацией по полям")
    void reportsValidationErrors() {
        webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"not-an-email","firstName":"",
                         "address":{"countryAlpha3":"RUS","countryAlpha2":"RU","city":"Moscow","addressLine":"x"}}
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://example.org/problems/validation-error")
                .jsonPath("$.errors.email").exists()
                .jsonPath("$.errors.lastName").exists();
    }

    @Test
    @DisplayName("невалидный email в query-параметре → 400")
    void reportsInvalidQueryParameter() {
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/v1/users/by-email")
                        .queryParam("email", "not-an-email")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    @DisplayName("неизвестная страна → 400")
    void rejectsUnknownCountry() {
        webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"email":"%s","firstName":"A","lastName":"B",
                         "address":{"countryAlpha3":"ZZZ","countryAlpha2":"ZZ","city":"X","addressLine":"y"}}
                        """.formatted(uniqueEmail()))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://example.org/problems/unknown-country");
    }

    @Test
    @DisplayName("битый UUID → 400, отсутствующий пользователь и путь → 404")
    void reportsNotFoundAndMalformedId() {
        webTestClient.get()
                .uri("/api/v1/users/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);

        webTestClient.get()
                .uri("/api/v1/users/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://example.org/problems/user-not-found");

        // Несуществующий маршрут обрабатывается ErrorWebExceptionHandler и тоже отдаёт problem+json
        webTestClient.get()
                .uri("/api/v1/unknown-resource")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://example.org/problems/resource-not-found");
    }

    @Test
    @DisplayName("PATCH обновляет вложенные сущности и сохраняет непереданные поля")
    void patchesNestedEntities() {
        String id = createUser(uniqueEmail());

        webTestClient.patch()
                .uri("/api/v1/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"lastName":"Петров-Старший",
                         "address":{"city":"Saint Petersburg"},
                         "individual":{"phoneNumber":"+79990000000"}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lastName").isEqualTo("Петров-Старший")
                .jsonPath("$.address.city").isEqualTo("Saint Petersburg")
                .jsonPath("$.address.addressLine").isEqualTo("ул. Пример, д. 1")
                .jsonPath("$.individual.phoneNumber").isEqualTo("+79990000000")
                .jsonPath("$.individual.passportNumber").isEqualTo("1234 567890");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select last_name, first_name from person.users where id = ?", UUID.fromString(id));
        assertThat(row.get("last_name")).isEqualTo("Петров-Старший");
        assertThat(row.get("first_name")).isEqualTo("Иван");
    }

    @Test
    @DisplayName("неудачный PATCH не оставляет частичных изменений")
    void failedPatchLeavesNoPartialChanges() {
        String id = createUser(uniqueEmail());

        webTestClient.patch()
                .uri("/api/v1/users/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"lastName":"Не должно сохраниться",
                         "address":{"countryAlpha3":"ZZZ","countryAlpha2":"ZZ"}}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        webTestClient.get()
                .uri("/api/v1/users/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.lastName").isEqualTo("Петров")
                .jsonPath("$.address.city").isEqualTo("Moscow");

        assertThat(jdbcTemplate.queryForObject(
                "select last_name from person.users where id = ?", String.class, UUID.fromString(id)))
                .isEqualTo("Петров");
    }

    @Test
    @DisplayName("конкурентное создание с одним email: одна запись, без осиротевших адресов")
    void concurrentCreatesKeepAggregateAtomic() throws Exception {
        String email = uniqueEmail();
        String firstBody = createBody(email, "ул. Первая, д. 1");
        String secondBody = createBody(email, "ул. Вторая, д. 2");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> first = () -> createAndGetStatus(firstBody);
            Callable<Integer> second = () -> createAndGetStatus(secondBody);
            Future<Integer> firstResult = pool.submit(first);
            Future<Integer> secondResult = pool.submit(second);

            List<Integer> statuses = List.of(
                    firstResult.get(60, TimeUnit.SECONDS),
                    secondResult.get(60, TimeUnit.SECONDS));

            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from person.users where lower(email) = lower(?)", Integer.class, email))
                .isEqualTo(1);

        // Адрес проигравшей транзакции не должен остаться в базе
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from person.addresses a
                where not exists (select 1 from person.users u where u.address_id = a.id)
                """, Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("аудит: ревизии с флагами изменённых полей, снимок при удалении")
    void writesAuditRevisions() {
        String email = uniqueEmail();
        String created = webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        var node = objectMapper.readTree(created);
        UUID userId = UUID.fromString(node.get("id").asText());
        UUID addressId = UUID.fromString(node.get("address").get("id").asText());

        webTestClient.patch()
                .uri("/api/v1/users/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"lastName\":\"Петров-Старший\",\"address\":{\"city\":\"Kazan\"}}")
                .exchange()
                .expectStatus().isOk();

        List<Map<String, Object>> revisions = jdbcTemplate.queryForList(
                "select revtype, last_name, last_name_mod, email_mod, filled_mod, address_mod, individual_mod "
                        + "from person.users_aud where id = ? order by rev", userId);

        assertThat(revisions).hasSize(2);
        assertThat(intValue(revisions.get(0).get("revtype"))).isZero();
        assertThat(intValue(revisions.get(1).get("revtype"))).isEqualTo(1);
        assertThat(revisions.get(1).get("last_name")).isEqualTo("Петров-Старший");
        assertThat(revisions.get(1).get("last_name_mod")).isEqualTo(true);
        assertThat(revisions.get(1).get("email_mod")).isEqualTo(false);
        assertThat(revisions.get(1).get("filled_mod")).isEqualTo(false);
        // address_mod отражает изменение связи users.address_id, а не полей адреса
        assertThat(revisions.get(1).get("address_mod")).isEqualTo(false);

        // Изменение полей адреса попадает в собственную аудитную таблицу адреса
        List<Map<String, Object>> addressRevisions = jdbcTemplate.queryForList(
                "select revtype, city from person.addresses_aud where id = ? order by rev", addressId);
        assertThat(addressRevisions).hasSize(2);
        assertThat(intValue(addressRevisions.get(1).get("revtype"))).isEqualTo(1);
        assertThat(addressRevisions.get(1).get("city")).isEqualTo("Kazan");

        // У пользователя ровно две ревизии, и обе зарегистрированы в revinfo.
        // Проверка не зависит от того, сколько ревизий оставили другие тесты.
        Integer revisionsOfUser = jdbcTemplate.queryForObject("""
                select count(*) from person.revinfo r
                where exists (select 1 from person.users_aud a where a.rev = r.rev and a.id = ?)
                """, Integer.class, userId);
        assertThat(revisionsOfUser).isEqualTo(2);
    }

    @Test
    @DisplayName("удаление агрегата: 204, каскад, ревизия удаления со снимком данных")
    void deletesWholeAggregate() {
        String email = uniqueEmail();
        String created = webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        var node = objectMapper.readTree(created);
        UUID userId = UUID.fromString(node.get("id").asText());
        UUID addressId = UUID.fromString(node.get("address").get("id").asText());
        UUID individualId = UUID.fromString(node.get("individual").get("id").asText());

        webTestClient.delete()
                .uri("/api/v1/users/{id}", userId)
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get()
                .uri("/api/v1/users/{id}", userId)
                .exchange()
                .expectStatus().isNotFound();

        assertThat(count("select count(*) from person.users where id = ?", userId)).isZero();
        assertThat(count("select count(*) from person.addresses where id = ?", addressId)).isZero();
        assertThat(count("select count(*) from person.individuals where id = ?", individualId)).isZero();

        Map<String, Object> deletion = jdbcTemplate.queryForMap(
                "select email, first_name, last_name from person.users_aud where id = ? and revtype = 2", userId);
        // Снимок состояния на момент удаления (аналог store_data_at_delete в Envers)
        assertThat(deletion.get("email")).isEqualTo(email);
        assertThat(deletion.get("last_name")).isEqualTo("Петров");

        assertThat(count("select count(*) from person.individuals_aud where id = ? and revtype = 2", individualId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from person.addresses_aud where id = ? and revtype = 2", Integer.class, addressId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("actuator отдаёт health, info и метрики Prometheus")
    void exposesActuator() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");

        webTestClient.get()
                .uri("/actuator/info")
                .exchange()
                .expectStatus().isOk();

        webTestClient.get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private int createAndGetStatus(String body) {
        return webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .returnResult(String.class)
                .getStatus()
                .value();
    }

    private String createUser(String email) {
        String created = webTestClient.post()
                .uri("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createBody(email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        return objectMapper.readTree(created).get("id").asText();
    }

    private String createBody(String email) {
        return createBody(email, "ул. Пример, д. 1");
    }

    private String createBody(String email, String addressLine) {
        return """
                {"email":"%s","firstName":"Иван","lastName":"Петров",
                 "address":{"countryAlpha3":"RUS","countryAlpha2":"RU","city":"Moscow","state":"Moscow",
                            "zipCode":"101000","addressLine":"%s"},
                 "individual":{"passportNumber":"1234 567890","phoneNumber":"+79991234567"}}
                """.formatted(email, addressLine);
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.org";
    }

    private Integer count(String sql, UUID id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    private int intValue(Object value) {
        return ((Number) value).intValue();
    }
}

package com.example.persons_service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционные проверки на реальном PostgreSQL (Testcontainers) для Spring MVC + JPA:
 * миграции Flyway, JPA-репозитории, транзакционные границы агрегата, собственный аудит,
 * RFC 9457 и actuator. HTTP-слой прогоняется через MockMvc (полный MVC-стек).
 */
@SpringBootTest(properties = {
        "management.tracing.enabled=false",
        "logging.level.root=WARN",
        "logging.level.com.example.persons_service=INFO"
})
@AutoConfigureMockMvc
@DisplayName("person-service (Spring MVC + JPA): интеграционные сценарии")
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
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("создание агрегата, чтение по id и по email без учёта регистра")
    void createsAndReadsAggregate() throws Exception {
        String email = uniqueEmail();

        String created = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Иван"))
                .andExpect(jsonPath("$.filled").value(true))
                .andExpect(jsonPath("$.address.countryAlpha3").value("RUS"))
                .andExpect(jsonPath("$.individual.status").value("NEW"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var root = objectMapper.readTree(created);
        assertThat(root.at("/individual/verifiedAt").isNull()).isTrue();
        assertThat(root.at("/individual/archivedAt").isNull()).isTrue();

        String id = root.get("id").asText();

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.address.addressLine").value("ул. Пример, д. 1"));

        mockMvc.perform(get("/api/v1/users/by-email").param("email", email.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    @DisplayName("повторный email → 409 в формате RFC 9457")
    void rejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://example.org/problems/email-already-exists"))
                .andExpect(jsonPath("$.title").value("Конфликт данных"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Пользователь с таким email уже существует"))
                .andExpect(jsonPath("$.instance").value("/api/v1/users"));
    }

    @Test
    @DisplayName("валидация запроса → 400 с детализацией по полям")
    void reportsValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","firstName":"",
                                 "address":{"countryAlpha3":"RUS","countryAlpha2":"RU","city":"Moscow","addressLine":"x"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://example.org/problems/validation-error"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.lastName").exists());
    }

    @Test
    @DisplayName("невалидный email в query-параметре → 400")
    void reportsInvalidQueryParameter() throws Exception {
        mockMvc.perform(get("/api/v1/users/by-email").param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("неизвестная страна → 400")
    void rejectsUnknownCountry() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","firstName":"A","lastName":"B",
                                 "address":{"countryAlpha3":"ZZZ","countryAlpha2":"ZZ","city":"X","addressLine":"y"}}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://example.org/problems/unknown-country"));
    }

    @Test
    @DisplayName("битый UUID → 400, отсутствующий пользователь и путь → 404")
    void reportsNotFoundAndMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/users/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://example.org/problems/user-not-found"));

        // Несуществующий маршрут обрабатывается advice и тоже отдаёт problem+json
        mockMvc.perform(get("/api/v1/unknown-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://example.org/problems/resource-not-found"));
    }

    @Test
    @DisplayName("PATCH обновляет вложенные сущности и сохраняет непереданные поля")
    void patchesNestedEntities() throws Exception {
        String id = createUser(uniqueEmail());

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastName":"Петров-Старший",
                                 "address":{"city":"Saint Petersburg"},
                                 "individual":{"phoneNumber":"+79990000000"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Петров-Старший"))
                .andExpect(jsonPath("$.address.city").value("Saint Petersburg"))
                .andExpect(jsonPath("$.address.addressLine").value("ул. Пример, д. 1"))
                .andExpect(jsonPath("$.individual.phoneNumber").value("+79990000000"))
                .andExpect(jsonPath("$.individual.passportNumber").value("1234 567890"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select last_name, first_name from person.users where id = ?", UUID.fromString(id));
        assertThat(row.get("last_name")).isEqualTo("Петров-Старший");
        assertThat(row.get("first_name")).isEqualTo("Иван");
    }

    @Test
    @DisplayName("неудачный PATCH не оставляет частичных изменений")
    void failedPatchLeavesNoPartialChanges() throws Exception {
        String id = createUser(uniqueEmail());

        mockMvc.perform(patch("/api/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lastName":"Не должно сохраниться",
                                 "address":{"countryAlpha3":"ZZZ","countryAlpha2":"ZZ"}}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Петров"))
                .andExpect(jsonPath("$.address.city").value("Moscow"));

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
    void writesAuditRevisions() throws Exception {
        String email = uniqueEmail();
        var createdNode = objectMapper.readTree(createUserResponse(email));
        UUID userId = UUID.fromString(createdNode.get("id").asText());
        UUID addressId = UUID.fromString(createdNode.at("/address/id").asText());

        mockMvc.perform(patch("/api/v1/users/{id}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"Петров-Старший\",\"address\":{\"city\":\"Kazan\"}}"))
                .andExpect(status().isOk());

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
        assertThat(revisions.get(1).get("address_mod")).isEqualTo(false);

        List<Map<String, Object>> addressRevisions = jdbcTemplate.queryForList(
                "select revtype, city from person.addresses_aud where id = ? order by rev", addressId);
        assertThat(addressRevisions).hasSize(2);
        assertThat(intValue(addressRevisions.get(1).get("revtype"))).isEqualTo(1);
        assertThat(addressRevisions.get(1).get("city")).isEqualTo("Kazan");

        Integer revisionsOfUser = jdbcTemplate.queryForObject("""
                select count(*) from person.revinfo r
                where exists (select 1 from person.users_aud a where a.rev = r.rev and a.id = ?)
                """, Integer.class, userId);
        assertThat(revisionsOfUser).isEqualTo(2);
    }

    @Test
    @DisplayName("удаление агрегата: 204, каскад, ревизия удаления со снимком данных")
    void deletesWholeAggregate() throws Exception {
        String email = uniqueEmail();
        var node = objectMapper.readTree(createUserResponse(email));
        UUID userId = UUID.fromString(node.get("id").asText());
        UUID addressId = UUID.fromString(node.at("/address/id").asText());
        UUID individualId = UUID.fromString(node.at("/individual/id").asText());

        mockMvc.perform(delete("/api/v1/users/{id}", userId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isNotFound());

        assertThat(count("select count(*) from person.users where id = ?", userId)).isZero();
        assertThat(count("select count(*) from person.addresses where id = ?", addressId)).isZero();
        assertThat(count("select count(*) from person.individuals where id = ?", individualId)).isZero();

        Map<String, Object> deletion = jdbcTemplate.queryForMap(
                "select email, first_name, last_name from person.users_aud where id = ? and revtype = 2", userId);
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
    void exposesActuator() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private int createAndGetStatus(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    private String createUserResponse(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String createUser(String email) throws Exception {
        return objectMapper.readTree(createUserResponse(email)).get("id").asText();
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

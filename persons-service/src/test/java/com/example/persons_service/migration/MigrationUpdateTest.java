package com.example.persons_service.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет сценарий обновления: база, поднятая миграциями из {@code main}
 * (V001–V006), должна обновляться новыми миграциями без потери данных и истории.
 *
 * <p>Если исторические миграции изменены (переписаны V005/V006), Flyway обнаружит
 * расхождение контрольных сумм и тест упадёт — именно этого не хватало в PR.</p>
 */
@DisplayName("Миграции: обновление существующей БД без потери данных и истории")
class MigrationUpdateTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    static {
        POSTGRES.start();
    }

    private static final UUID KEPT_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("БД из main (V001–V006) обновляется новыми миграциями, данные и аудит целы")
    void updatesExistingDatabaseFromMainSchema() throws Exception {
        // 1. Применяем ровно то, что лежит в main.
        flyway("6").migrate();

        // 2. Наполняем БД так, как она выглядела до обновления: пользователь + ревизия аудита.
        execute("""
                INSERT INTO person.users (id, email, first_name, last_name, filled)
                VALUES ('%s', 'kept@example.org', 'Иван', 'Петров', false)
                """.formatted(KEPT_USER));
        execute("INSERT INTO person.revinfo (rev, revtstmp) VALUES (1, 1)");
        execute("""
                INSERT INTO person.users_aud (rev, revtype, id, email, first_name, last_name, filled)
                VALUES (1, 0, '%s', 'kept@example.org', 'Иван', 'Петров', false)
                """.formatted(KEPT_USER));

        // 3. Накатываем новые миграции и валидируем контрольные суммы.
        Flyway flyway = flyway(null);
        flyway.migrate();
        flyway.validate();

        // 4. Доменные данные и история аудита не потеряны.
        assertThat(queryInt("select count(*) from person.users where email = 'kept@example.org'"))
                .isEqualTo(1);
        assertThat(queryInt("select count(*) from person.users_aud where id = '" + KEPT_USER + "'"))
                .isEqualTo(1);
        assertThat(queryInt("select count(*) from person.revinfo where rev = 1"))
                .isEqualTo(1);

        // 5. Схема получила новые колонки, а неиспользуемая — удалена.
        assertThat(hasColumn("users", "deleted_at")).isTrue();
        assertThat(hasColumn("users_aud", "address_id_mod")).isTrue();
        assertThat(hasColumn("users_aud", "deleted_at")).isTrue();
        assertThat(hasColumn("users_aud", "deleted_at_mod")).isTrue();
        assertThat(hasColumn("addresses_aud", "country_id")).isTrue();
        assertThat(hasColumn("addresses_aud", "country_id_mod")).isTrue();
        assertThat(hasColumn("individuals_aud", "user_id_mod")).isTrue();
        assertThat(hasColumn("users_aud", "individual_mod")).isFalse();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .schemas("person")
                .defaultSchema("person");
        if (target != null) {
            config = config.target(MigrationVersion.fromVersion(target));
        }
        return config.load();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int queryInt(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private boolean hasColumn(String table, String column) throws SQLException {
        return queryInt("""
                select count(*) from information_schema.columns
                where table_schema = 'person' and table_name = '%s' and column_name = '%s'
                """.formatted(table, column)) > 0;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}

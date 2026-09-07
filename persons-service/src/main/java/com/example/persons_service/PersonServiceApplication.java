package com.example.persons_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * person-service — микросервис предметной области: хранит и изменяет агрегат
 * пользователя (учётная запись + адрес + индивидуальные данные).
 *
 * <p>Стек: WebFlux (реактивный HTTP) + R2DBC (неблокирующий доступ к БД).
 * Flyway работает по JDBC на старте приложения — для миграций это нормально
 * и не делает рабочий путь запроса блокирующим.</p>
 *
 * <p>Сервис не обращается к Keycloak: регистрация во внешней системе идентификации —
 * ответственность оркестратора individuals-api.</p>
 *
 * <p>Пакет сгенерированного кода ({@code net.example.person.api}) лежит вне пакета
 * приложения, поэтому сканирование расширено явно: сгенерированный
 * {@code UsersApiController} должен попасть в контекст, иначе маршруты не регистрируются.</p>
 *
 * <p>Автоконфигурация JDBC-транзакций исключена: JDBC-источник данных нужен только Flyway
 * на старте, а в контексте должен остаться ровно один менеджер транзакций — реактивный
 * ({@code R2dbcTransactionManager}), иначе {@code @Transactional} не может выбрать.</p>
 */
@SpringBootApplication(
        scanBasePackages = {"com.example.persons_service", "net.example.person.api"},
        exclude = DataSourceTransactionManagerAutoConfiguration.class)
public class PersonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonServiceApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

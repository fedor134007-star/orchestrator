package com.example.persons_service;

import com.example.persons_service.config.PersonServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * person-service — микросервис предметной области: хранит и изменяет агрегат
 * пользователя (учётная запись + адрес + индивидуальные данные).
 *
 * <p>Стек: Spring MVC (Servlet) + Spring Data JPA + Hibernate. Flyway применяет миграции
 * по JDBC на старте приложения.</p>
 *
 * <p>Сервис не обращается к Keycloak: регистрация во внешней системе идентификации —
 * ответственность оркестратора individuals-api.</p>
 *
 * <p>Пакет сгенерированного кода ({@code net.example.person.api}) лежит вне пакета
 * приложения, поэтому сканирование расширено явно: сгенерированный
 * {@code UsersApiController} должен попасть в контекст, иначе маршруты не регистрируются.</p>
 */
@SpringBootApplication(scanBasePackages = {"com.example.persons_service", "net.example.person.api"})
@EnableConfigurationProperties(PersonServiceProperties.class)
public class PersonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonServiceApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

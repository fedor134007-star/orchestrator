package com.example.persons_service.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * JDBC-подключение для Flyway.
 *
 * <p>Spring Boot не создаёт JDBC {@code DataSource}, если в контексте уже есть реактивный
 * {@code ConnectionFactory}: это защита от смешивания блокирующего и неблокирующего доступа
 * к одной базе. Рабочий путь запроса идёт по R2DBC, но миграции Flyway выполняются по JDBC,
 * поэтому источник данных объявляется явно.</p>
 *
 * <p>Пул намеренно маленький: он используется только на старте приложения, пока
 * применяются миграции, и не обслуживает запросы.</p>
 */
@Configuration(proxyBeanMethods = false)
public class FlywayDataSourceConfiguration {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource flywayDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}

package com.example.persons_service.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean(initMethod = "migrate")
    public Flyway flyway(
            @Value("${spring.flyway.url}") String url,
            @Value("${spring.flyway.user}") String user,
            @Value("${spring.flyway.password}") String password,
            @Value("${spring.flyway.locations:classpath:db/migration}") String locations) {

        return Flyway.configure()
                .dataSource(url, user, password)
                .locations(locations)
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
    }
}
package com.example.persons_service.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Значения конфигурации сервиса, вынесенные из {@code application.yaml}
 * в {@code person-service.properties} (prefix {@code person-service}).
 *
 * <p>Класс даёт типизацию и валидацию на старте: неверное значение уронит приложение
 * при подъёме, а не в рантайме. Параметры пула подхватываются HikariCP через
 * плейсхолдеры в {@code spring.datasource.hikari.*}.</p>
 */
@Validated
@ConfigurationProperties(prefix = "person-service")
public class PersonServiceProperties {

    @Valid
    private final Pool pool = new Pool();

    public Pool getPool() {
        return pool;
    }

    public static class Pool {

        @Min(1)
        private int initialSize = 5;

        @Min(1)
        private int maxSize = 20;

        @NotNull
        private Duration maxIdleTime = Duration.ofMinutes(30);

        public int getInitialSize() {
            return initialSize;
        }

        public void setInitialSize(int initialSize) {
            this.initialSize = initialSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public Duration getMaxIdleTime() {
            return maxIdleTime;
        }

        public void setMaxIdleTime(Duration maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
        }
    }
}

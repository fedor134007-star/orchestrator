package com.example.persons_service.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Пул соединений HikariCP, собранный из типизированных значений
 * {@link PersonServiceProperties} (файл person-service.properties).
 *
 * <p>Значения собраны здесь, а не через {@code spring.datasource.hikari.*}, потому что
 * {@code idle-timeout} у Hikari — это {@code long} в миллисекундах, а в конфиге мы хотим
 * читаемое {@code 30m} ({@link java.time.Duration}).</p>
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfiguration {

    @Bean
    public DataSource dataSource(PersonServiceProperties properties,
                                 @Value("${spring.datasource.url}") String url,
                                 @Value("${spring.datasource.username}") String username,
                                 @Value("${spring.datasource.password}") String password) {

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setPoolName("person-service-pool");
        dataSource.setMinimumIdle(properties.getPool().getInitialSize());
        dataSource.setMaximumPoolSize(properties.getPool().getMaxSize());
        dataSource.setIdleTimeout(properties.getPool().getMaxIdleTime().toMillis());
        return dataSource;
    }
}

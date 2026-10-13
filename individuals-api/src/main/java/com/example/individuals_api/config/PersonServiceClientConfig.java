package com.example.individuals_api.config;

import net.example.person.client.api.UsersApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Клиент person-service, собранный из опубликованного артефакта {@code person-service-client}.
 *
 * <p>Используются Spring HTTP Service Clients ({@code @HttpExchange} + {@code HttpServiceProxyFactory}),
 * а не OpenFeign: в экосистеме Spring OpenFeign считается функционально завершённым проектом.</p>
 *
 * <p>Обработку статусов выполняет адаптер {@code PersonsClientImpl}: он различает
 * «не найдено» (404) и реальные сбои, поэтому статус-хендлеры здесь не настраиваются.</p>
 */
@Configuration
public class PersonServiceClientConfig {

    @Bean
    public RestClient personServiceRestClient(
            @Value("${person-service.base-url:http://localhost:8092}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    public UsersApi personServiceUsersApi(RestClient personServiceRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(personServiceRestClient))
                .build();
        return factory.createClient(UsersApi.class);
    }
}

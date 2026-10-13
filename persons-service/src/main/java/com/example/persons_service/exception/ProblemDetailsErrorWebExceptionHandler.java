package com.example.persons_service.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Приводит к RFC 9457 ошибки, которые не доходят до {@code @RestControllerAdvice}:
 * например, запрос к несуществующему пути ({@code DispatcherHandler} поднимает
 * {@code ResponseStatusException(404)} вне обработчика).
 *
 * <p>Порядок {@code -2} ставит обработчик перед стандартным
 * {@code DefaultErrorWebExceptionHandler} Boot.</p>
 */
@Component
@Order(-2)
public class ProblemDetailsErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsErrorWebExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public ProblemDetailsErrorWebExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        HttpStatus status = resolveStatus(throwable);
        boolean notFound = status == HttpStatus.NOT_FOUND;
        if (!notFound) {
            log.error("Необработанная ошибка запроса {}", exchange.getRequest().getPath(), throwable);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, notFound
                ? "Запрошенный ресурс не существует"
                : "Непредвиденная ошибка сервиса");
        problem.setType(notFound ? ProblemTypes.RESOURCE_NOT_FOUND : ProblemTypes.INTERNAL_ERROR);
        problem.setTitle(notFound ? "Ресурс не найден" : "Внутренняя ошибка");
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problem);
        } catch (Exception serializationFailure) {
            body = ("{\"title\":\"" + problem.getTitle() + "\",\"status\":" + status.value() + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private HttpStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof ResponseStatusException statusException) {
            HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}

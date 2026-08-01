package com.raulbolivar.proxy.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raulbolivar.proxy.infrastructure.StoredProcedureExecutionException;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@Component
@Order(-2)
public class GlobalErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) return Mono.error(error);

        HttpStatus status;
        if (error instanceof IllegalArgumentException) status = HttpStatus.BAD_REQUEST;
        else if (error instanceof NoSuchElementException) status = HttpStatus.NOT_FOUND;
        else if (error instanceof StoredProcedureExecutionException) status = HttpStatus.BAD_GATEWAY;
        else status = HttpStatus.INTERNAL_SERVER_ERROR;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", error.getMessage());
        body.put("path", exchange.getRequest().getPath().value());

        if (error instanceof StoredProcedureExecutionException ex) {
            body.put("sqlState", ex.sqlState());
            body.put("vendorCode", ex.vendorCode());
        }

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception serializationError) {
            return Mono.error(serializationError);
        }
    }
}

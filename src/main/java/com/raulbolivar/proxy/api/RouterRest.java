package com.raulbolivar.proxy.api;

import com.raulbolivar.proxy.api.handler.StoredProcedureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class RouterRest {
    @Bean
    RouterFunction<ServerResponse> storedProcedureRoutes(StoredProcedureHandler handler) {
        return route()
                .path("/api/v1/stored-procedures", builder -> builder
                        .GET("", accept(MediaType.APPLICATION_JSON), handler::list)
                        .GET("/{procedure}", accept(MediaType.APPLICATION_JSON), handler::describe)
                        .POST("/execute", contentType(MediaType.APPLICATION_JSON), handler::execute))
                .build();
    }
}

package com.raulbolivar.proxy.api;

import com.raulbolivar.proxy.application.StoredProcedureService;
import com.raulbolivar.proxy.domain.ExecuteProcedureCommand;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class StoredProcedureHandler {

    private final StoredProcedureService service;
    private final Validator validator;

    public StoredProcedureHandler(StoredProcedureService service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    public Mono<ServerResponse> execute(ServerRequest request) {
        return request.bodyToMono(ExecuteProcedureRequest.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("El body es requerido")))
                .flatMap(this::validate)
                .flatMap(body -> service.execute(new ExecuteProcedureCommand(body.schema(), body.procedure(), body.parameters())))
                .flatMap(result -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result));
    }

    public Mono<ServerResponse> describe(ServerRequest request) {
        return service.describe(request.queryParam("schema").orElse(null), request.pathVariable("procedure"))
                .flatMap(result -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result));
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return service.allowedProcedures()
                .flatMap(result -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result));
    }

    private Mono<ExecuteProcedureRequest> validate(ExecuteProcedureRequest body) {
        var errors = new BeanPropertyBindingResult(body, "request");
        validator.validate(body, errors);
        if (errors.hasErrors()) return Mono.error(new IllegalArgumentException(errors.getAllErrors().getFirst().getDefaultMessage()));
        return Mono.just(body);
    }
}

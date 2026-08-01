package com.raulbolivar.proxy.application;

import com.raulbolivar.proxy.config.StoredProcedureProperties;
import com.raulbolivar.proxy.domain.ExecuteProcedureCommand;
import com.raulbolivar.proxy.domain.ProcedureDefinition;
import com.raulbolivar.proxy.domain.ProcedureExecutionResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class StoredProcedureService {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[A-Za-z_]\\w*$");

    private final StoredProcedureGateway    gateway;
    private final StoredProcedureProperties properties;

    public StoredProcedureService(StoredProcedureGateway gateway,
                                  StoredProcedureProperties properties) {
        this.gateway = gateway;
        this.properties = properties;
    }

    public Mono<ProcedureDefinition> describe(String databaseKey, String schema, String procedure) {
        String effectiveSchema = normalize(schema);
        validate(effectiveSchema, procedure);
        return gateway.describe(databaseKey, effectiveSchema, procedure);
    }

    public Mono<ProcedureExecutionResult> execute(ExecuteProcedureCommand command) {
        String schema = normalize(command.schema());
        validate(schema, command.procedure());
        return gateway.execute(new ExecuteProcedureCommand(command.databaseKey(), schema, command.procedure(), command.parameters()));
    }

    public Mono<List<String>> allowedProcedures(String databaseKey) {
        return gateway.allowedProcedures(databaseKey).collectList();
    }

    private String normalize(String schema) {
        return schema == null || schema.isBlank() ? properties.defaultSchema() : schema;
    }

    private void validate(String schema, String procedure) {
        if (!SQL_IDENTIFIER.matcher(schema).matches() || procedure == null || !SQL_IDENTIFIER.matcher(procedure).matches())
            throw new IllegalArgumentException("Schema o procedimiento inválido");
        String qualified = schema + "." + procedure;
        if (!properties.allowed().isEmpty() && properties.allowed().stream().noneMatch(p -> p.equalsIgnoreCase(qualified)))
            throw new IllegalArgumentException("Procedimiento no permitido: " + qualified);
    }
}

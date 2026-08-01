package com.raulbolivar.proxy.domain;

import java.util.Map;

public record ExecuteProcedureCommand(
        String databaseKey,
        String schema,
        String procedure,
        Map<String, Object> parameters
) {
    public ExecuteProcedureCommand {
        databaseKey = databaseKey == null ? "" : databaseKey.trim();
        schema = schema == null || schema.isBlank() ? "dbo" : schema.trim();
        procedure = procedure == null ? "" : procedure.trim();
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}

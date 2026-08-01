package com.raulbolivar.proxy.domain;

import java.util.Map;

public record ExecuteProcedureCommand(
        String schema,
        String procedure,
        Map<String, Object> parameters
) {
    public ExecuteProcedureCommand {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}

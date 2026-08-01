package com.raulbolivar.proxy.domain;

import java.util.List;

public record ProcedureDefinition(
        String schema,
        String name,
        List<ProcedureParameter> parameters
) { }
package com.raulbolivar.proxy.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record ExecuteProcedureRequest(
        String schema,
        @NotBlank String procedure,
        Map<String, Object> parameters
) { }

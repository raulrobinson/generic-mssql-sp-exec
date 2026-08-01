package com.raulbolivar.proxy.domain;

import java.util.List;
import java.util.Map;

public record ProcedureExecutionResult(
        String databaseKey,
        String schema,
        String procedure,
        Map<String, Object> outputParameters,
        List<List<Map<String, Object>>> resultSets,
        List<Integer> updateCounts
) {
}

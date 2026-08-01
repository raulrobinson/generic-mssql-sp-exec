package com.raulbolivar.proxy.application;

import com.raulbolivar.proxy.domain.ExecuteProcedureCommand;
import com.raulbolivar.proxy.domain.ProcedureDefinition;
import com.raulbolivar.proxy.domain.ProcedureExecutionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StoredProcedureGateway {

    Mono<ProcedureDefinition> describe(String databaseKey, String schema, String procedure);

    Mono<ProcedureExecutionResult> execute(ExecuteProcedureCommand command);

    Flux<String> allowedProcedures(String databaseKey);
}

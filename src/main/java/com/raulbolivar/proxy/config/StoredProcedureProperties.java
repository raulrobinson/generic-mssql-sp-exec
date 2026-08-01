package com.raulbolivar.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "app.stored-procedures")
public record StoredProcedureProperties(
        String defaultSchema,
        Set<String> allowed,
        Duration timeout,
        int maxRows
) {
    public StoredProcedureProperties {
        defaultSchema = defaultSchema == null || defaultSchema.isBlank() ? "dbo" : defaultSchema;
        allowed = allowed == null ? Set.of() : Set.copyOf(allowed);
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        maxRows = maxRows <= 0 ? 1000 : maxRows;
    }
}

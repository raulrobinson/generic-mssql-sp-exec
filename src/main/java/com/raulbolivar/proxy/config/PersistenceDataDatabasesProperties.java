package com.raulbolivar.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "persistence")
public record PersistenceDataDatabasesProperties(Map<String, Database> databases) {

    public record Database(String r2dbcUrl, String username, String password, Pool pool) {
        public Database {
            pool = pool == null
                    ? new Pool(1, 10, Duration.ofMinutes(10), Duration.ofSeconds(15), "SELECT 1")
                    : pool;
        }
    }

    public record Pool(int initialSize, int maxSize, Duration maxIdleTime,
                       Duration maxAcquireTime, String validationQuery) {
        public Pool {
            if (initialSize < 0) initialSize = 0;
            if (maxSize <= 0) maxSize = 10;
            if (maxIdleTime == null) maxIdleTime = Duration.ofMinutes(10);
            if (maxAcquireTime == null) maxAcquireTime = Duration.ofSeconds(15);
            if (validationQuery == null || validationQuery.isBlank()) validationQuery = "SELECT 1";
        }
    }
}

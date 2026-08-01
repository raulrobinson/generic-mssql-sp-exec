package com.raulbolivar.proxy.infrastructure;

import com.raulbolivar.proxy.config.PersistenceDataDatabasesProperties;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import jakarta.annotation.PreDestroy;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class ConnectionFactoryRegistry {

    private final Map<String, ConnectionPool> pools;
    private final Map<String, DatabaseClient> clients;

    public ConnectionFactoryRegistry(PersistenceDataDatabasesProperties properties) {
        if (properties.databases() == null || properties.databases().isEmpty()) {
            throw new IllegalStateException("No hay bases configuradas en persistence.databases");
        }

        Map<String, ConnectionPool> poolMap = new LinkedHashMap<>();
        Map<String, DatabaseClient> clientMap = new LinkedHashMap<>();

        properties.databases().forEach((databaseKey, config) -> {
            String key = normalize(databaseKey);

            ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(config.r2dbcUrl())
                    .mutate()
                    .option(ConnectionFactoryOptions.USER, config.username())
                    .option(ConnectionFactoryOptions.PASSWORD, config.password())
                    .build();

            ConnectionFactory delegate = ConnectionFactories.get(options);
            PersistenceDataDatabasesProperties.Pool pool = config.pool();

            ConnectionPoolConfiguration poolConfiguration =
                    ConnectionPoolConfiguration.builder(delegate)
                            .name("persistence-" + key.toLowerCase(Locale.ROOT))
                            .initialSize(pool.initialSize())
                            .maxSize(pool.maxSize())
                            .maxIdleTime(pool.maxIdleTime())
                            .maxAcquireTime(pool.maxAcquireTime())
                            .validationQuery(pool.validationQuery())
                            .build();

            ConnectionPool connectionPool = new ConnectionPool(poolConfiguration);
            poolMap.put(key, connectionPool);
            clientMap.put(key, DatabaseClient.create(connectionPool));
        });

        this.pools = Map.copyOf(poolMap);
        this.clients = Map.copyOf(clientMap);
    }

    public ConnectionFactory getConnectionFactory(String databaseKey) {
        ConnectionPool pool = pools.get(normalize(databaseKey));
        if (pool == null) throw unknownDatabase(databaseKey);
        return pool;
    }

    public DatabaseClient getDatabaseClient(String databaseKey) {
        DatabaseClient client = clients.get(normalize(databaseKey));
        if (client == null) throw unknownDatabase(databaseKey);
        return client;
    }

    public java.util.Set<String> availableDatabaseKeys() {
        return pools.keySet();
    }

    @PreDestroy
    public void dispose() {
        pools.values().forEach(pool -> Mono.from(pool.disposeLater()).block());
    }

    private IllegalArgumentException unknownDatabase(String databaseKey) {
        return new IllegalArgumentException(
                "databaseKey no configurado: " + databaseKey
                        + ". Valores permitidos: " + String.join(", ", pools.keySet())
        );
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }
}

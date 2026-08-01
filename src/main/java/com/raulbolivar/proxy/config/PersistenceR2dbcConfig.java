package com.raulbolivar.proxy.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.Duration;

@Configuration
public class PersistenceR2dbcConfig {

    @Bean(destroyMethod = "dispose")
    @Primary
    public ConnectionPool persistenceConnectionFactory(
            @Value("${persistence.datasource.r2dbc-url}") String url,
            @Value("${persistence.datasource.username}") String username,
            @Value("${persistence.datasource.password}") String password,
            @Value("${persistence.datasource.pool.initial-size:1}") int initialSize,
            @Value("${persistence.datasource.pool.max-size:10}") int maxSize,
            @Value("${persistence.datasource.pool.max-idle-time:10m}") Duration maxIdleTime,
            @Value("${persistence.datasource.pool.max-acquire-time:15s}") Duration maxAcquireTime
    ) {
        ConnectionFactoryOptions options = ConnectionFactoryOptions.parse(url)
                .mutate()
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build();

        ConnectionFactory delegate = ConnectionFactories.get(options);

        ConnectionPoolConfiguration poolConfiguration = ConnectionPoolConfiguration
                .builder(delegate)
                .initialSize(initialSize)
                .maxSize(maxSize)
                .maxIdleTime(maxIdleTime)
                .maxAcquireTime(maxAcquireTime)
                .validationQuery("SELECT 1")
                .build();

        return new ConnectionPool(poolConfiguration);
    }

    @Bean
    @Primary
    public DatabaseClient persistenceDatabaseClient(
            @Qualifier("persistenceConnectionFactory") ConnectionFactory connectionFactory
    ) {
        return DatabaseClient.create(connectionFactory);
    }
}

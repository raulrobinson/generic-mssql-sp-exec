package com.raulbolivar.proxy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PersistenceDataDatabasesProperties.class)
public class PersistenceDataConfiguration {
}

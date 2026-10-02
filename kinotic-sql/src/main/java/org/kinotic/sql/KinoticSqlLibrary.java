package org.kinotic.sql;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * This class provides the necessary configuration annotations to enable this library for use in Spring boot applications
 */
@Configuration
@EnableConfigurationProperties
@ComponentScan
public class KinoticSqlLibrary {

    /**
     * The name of the {@code ElasticsearchAsyncClient} bean that the {@code MigrationExecutor} and the statement
     * executors run against. An application using this library must provide a bean with this name.
     */
    public static final String ELASTIC_CLIENT = "kinoticSqlElasticClient";

}

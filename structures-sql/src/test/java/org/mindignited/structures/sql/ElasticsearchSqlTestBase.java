package org.mindignited.structures.sql;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;

@SpringBootTest
public abstract class ElasticsearchSqlTestBase {

    public static final ElasticsearchContainer ELASTICSEARCH_CONTAINER;

    static {
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");

        ELASTICSEARCH_CONTAINER = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.18.1");
        ELASTICSEARCH_CONTAINER.withEnv("discovery.type", "single-node")
                               .withEnv("xpack.security.enabled", "false");

        // We need this until this is resolved https://github.com/elastic/elasticsearch/issues/118583
        if(osName != null && osName.startsWith("Mac") && osArch != null && osArch.equals("aarch64")){
            ELASTICSEARCH_CONTAINER.withEnv("_JAVA_OPTIONS", "-XX:UseSVE=0");
        }

        ELASTICSEARCH_CONTAINER.start();
    }

    @Autowired
    protected ElasticsearchAsyncClient asyncClient;


    @DynamicPropertySource
    static void registerElasticProperties(DynamicPropertyRegistry registry) {
        String[] parts = ELASTICSEARCH_CONTAINER.getHttpHostAddress().split(":");
        registry.add("elasticsearch.test.hostname", () -> parts[0]);
        registry.add("elasticsearch.test.port", () -> Integer.parseInt(parts[1]));
    }
} 
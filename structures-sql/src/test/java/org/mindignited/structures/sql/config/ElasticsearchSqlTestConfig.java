package org.mindignited.structures.sql.config;

import java.util.List;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

/**
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
@Configuration
public class ElasticsearchSqlTestConfig {

    @Value("${elasticsearch.test.hostname}")
    private String hostname;

    @Value("${elasticsearch.test.port}")
    private int port;

    @Bean
    public ElasticsearchTransport elasticsearchTransport(){
        RestClientBuilder builder = RestClient.builder(List.of(new HttpHost(hostname, port, "http")).toArray(new HttpHost[0]));

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(60000)
                .setSocketTimeout(60000));

        RestClient restClient = builder.build();

        // Create the transport with a Jackson mapper
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    @ConditionalOnProperty(prefix = "structures-sql-test", name = "enabled", havingValue = "true")
    public ElasticsearchAsyncClient elasticsearchAsyncClient(ElasticsearchTransport transport){
        return new ElasticsearchAsyncClient(transport);
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport){
        return new ElasticsearchClient(transport);
    }

}


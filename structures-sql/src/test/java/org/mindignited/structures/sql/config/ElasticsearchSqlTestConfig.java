package org.mindignited.structures.sql.config;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
        Rest5ClientBuilder builder = Rest5Client.builder(List.of(new HttpHost("http", hostname, port)).toArray(new HttpHost[0]));

        builder.setConnectionConfigCallback(connectionConfig -> connectionConfig
                .setConnectTimeout(Timeout.of(60000, TimeUnit.MILLISECONDS))
                .setSocketTimeout(Timeout.of(60000, TimeUnit.MILLISECONDS)));

        // Create the transport with a Jackson mapper
        return new Rest5ClientTransport(builder.build(), new Jackson3JsonpMapper());
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


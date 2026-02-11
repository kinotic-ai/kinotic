package org.kinotic.migration.config;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
@Configuration
@RequiredArgsConstructor
public class MigrationElasticsearchConfig {

    private final MirationProperties properties;

    @Bean
    public ElasticsearchAsyncClient elasticsearchAsyncClient(JsonpMapper jsonpMapper){

        var builder = Rest5Client.builder(new HttpHost(properties.getElasticScheme(),
                                                       properties.getElasticHost(),
                                                       properties.getElasticPort()
        ));

        if(properties.hasElasticUsernameAndPassword()){
            String credentials = properties.getElasticUsername() + ":" + properties.getElasticPassword();
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "Basic " + encodedCredentials)
            });
        }

        Rest5Client rest5Client = builder.build();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new Rest5ClientTransport(rest5Client, jsonpMapper);

        return new ElasticsearchAsyncClient(transport);
    }

    @Bean
    public JsonpMapper jsonpMapper(JsonMapper jsonMapper){
        return new Jackson3JsonpMapper(jsonMapper);
    }

}


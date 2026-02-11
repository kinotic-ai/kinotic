package org.kinotic.persistence.internal.config;


import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
@Configuration
public class StructuresElasticsearchConfig {

    private final StructuresProperties structuresProperties;

    public StructuresElasticsearchConfig(StructuresProperties structuresProperties) {
        this.structuresProperties = structuresProperties;
    }

    @Bean
    public ElasticsearchAsyncClient elasticsearchAsyncClient(JsonpMapper jsonpMapper){
        HttpHost[] hosts = structuresProperties.getElasticConnections()
                                               .stream()
                                               .map(v -> new HttpHost(v.getScheme(), v.getHost(), v.getPort()))
                                               .toArray(HttpHost[]::new);

        var builder = Rest5Client.builder(hosts);

        if(structuresProperties.hasElasticUsernameAndPassword()){
            String credentials = structuresProperties.getElasticUsername() + ":" + structuresProperties.getElasticPassword();
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "Basic " + encodedCredentials)
            });
        }

        Timeout connectTimeout = Timeout.of(structuresProperties.getElasticConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        Timeout socketTimeout = Timeout.of(structuresProperties.getElasticSocketTimeout().toMillis(), TimeUnit.MILLISECONDS);
        Timeout responseTimeout = Timeout.of(structuresProperties.getElasticSocketTimeout().toMillis(), TimeUnit.MILLISECONDS);

        builder.setConnectionConfigCallback(connectionConfig -> connectionConfig
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout));
        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setResponseTimeout(responseTimeout));

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


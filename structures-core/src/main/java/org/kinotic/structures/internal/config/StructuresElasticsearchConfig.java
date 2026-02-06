package org.kinotic.structures.internal.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.kinotic.structures.api.config.StructuresProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;

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
                                               .map(v -> new HttpHost(v.getHost(), v.getPort(), v.getScheme()))
                                               .toArray(HttpHost[]::new);

        RestClientBuilder builder = RestClient.builder(hosts);

        if(structuresProperties.hasElasticUsernameAndPassword()){
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                                                   new UsernamePasswordCredentials(structuresProperties.getElasticUsername(),
                                                                                   structuresProperties.getElasticPassword()));

                return httpClientBuilder
                        .setDefaultCredentialsProvider(credentialsProvider);
            });
        }

        int connectTimeout = Long.valueOf(structuresProperties.getElasticConnectionTimeout().toMillis()).intValue();
        int socketTimeout = Long.valueOf(structuresProperties.getElasticSocketTimeout().toMillis()).intValue();

        builder.setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout));

        RestClient restClient = builder.build();

        // Create the transport with a Jackson mapper
        ElasticsearchTransport transport = new RestClientTransport(restClient, jsonpMapper);

        return new ElasticsearchAsyncClient(transport);
    }

    @Bean
    public JsonpMapper jsonpMapper(ObjectMapper objectMapper){
        return new JacksonJsonpMapper(objectMapper);
    }

}


package org.kinotic.domain.internal.config;


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
import org.kinotic.domain.api.config.DomainPersistenceProperties;
import org.kinotic.domain.api.config.DomainProperties;
import org.kinotic.domain.api.config.ElasticClusterProperties;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.sql.KinoticSqlLibrary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Created by Navíd Mitchell 🤪 on 4/26/23.
 */
@Configuration
public class KinoticElasticsearchConfig {

    private final DomainProperties domainProperties;

    public KinoticElasticsearchConfig(KinoticDomainProperties kinoticDomainProperties) {
        this.domainProperties = kinoticDomainProperties.getDomain();
    }

    @Bean
    @Primary
    public ElasticsearchAsyncClient elasticsearchAsyncClient(JsonpMapper jsonpMapper){
        return createClient(domainProperties.getElastic(), jsonpMapper);
    }

    // kinotic-sql's statement executors run project migrations and named queries, which act on entity data
    @Bean({DomainUtil.ENTITY_DATA_ELASTIC_CLIENT, KinoticSqlLibrary.ELASTIC_CLIENT})
    public ElasticsearchAsyncClient entityDataElasticClient(JsonpMapper jsonpMapper){
        return createClient(domainProperties.getPersistence().getElastic(), jsonpMapper);
    }

    @Bean
    @Primary
    public CrudServiceTemplate crudServiceTemplate(ElasticsearchAsyncClient elasticsearchAsyncClient,
                                                   ObjectMapper objectMapper){
        return new CrudServiceTemplate(elasticsearchAsyncClient, objectMapper);
    }

    @Bean(DomainUtil.ENTITY_DATA_CRUD_SERVICE_TEMPLATE)
    public CrudServiceTemplate entityDataCrudServiceTemplate(@Qualifier(DomainUtil.ENTITY_DATA_ELASTIC_CLIENT)
                                                             ElasticsearchAsyncClient entityDataElasticClient,
                                                             ObjectMapper objectMapper){
        return new CrudServiceTemplate(entityDataElasticClient, objectMapper);
    }

    @Bean
    public JsonpMapper jsonpMapper(JsonMapper jsonMapper){
        return new Jackson3JsonpMapper(jsonMapper);
    }

    /**
     * Makes the {@link DomainPersistenceProperties} bean available for use by other beans without needing to inject {@link KinoticDomainProperties}
     */
    @Bean
    public DomainPersistenceProperties domainPersistenceProperties(){
        return domainProperties.getPersistence();
    }

    private ElasticsearchAsyncClient createClient(ElasticClusterProperties cluster, JsonpMapper jsonpMapper){
        HttpHost[] hosts = cluster.getConnections()
                                  .stream()
                                  .map(v -> new HttpHost(v.getScheme(), v.getHost(), v.getPort()))
                                  .toArray(HttpHost[]::new);

        var builder = Rest5Client.builder(hosts);

        if(cluster.hasUsernameAndPassword()){
            String credentials = cluster.getUsername() + ":" + cluster.getPassword();
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "Basic " + encodedCredentials)
            });
        }

        Timeout connectTimeout = Timeout.of(cluster.getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        Timeout socketTimeout = Timeout.of(cluster.getSocketTimeout().toMillis(), TimeUnit.MILLISECONDS);
        Timeout responseTimeout = Timeout.of(cluster.getSocketTimeout().toMillis(), TimeUnit.MILLISECONDS);

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

}

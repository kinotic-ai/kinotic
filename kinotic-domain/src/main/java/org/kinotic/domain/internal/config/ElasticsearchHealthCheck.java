package org.kinotic.domain.internal.config;

import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Registers the {@code elasticsearch} and {@code entityDataElasticsearch} procedures of the server's
 * {@link HealthChecks}. The {@code elasticsearch} procedure reports the cluster that stores the platform's domain
 * objects healthy while its last periodic health request for the platform's indices succeeded, and the
 * {@code entityDataElasticsearch} procedure reports the entity data cluster healthy while its last periodic health
 * request succeeded.
 * Created by Navíd Mitchell 🤪 on 5/30/23.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchHealthCheck {

    private final ElasticsearchAsyncClient esAsyncClient;
    @Qualifier(DomainUtil.ENTITY_DATA_ELASTIC_CLIENT)
    private final ElasticsearchAsyncClient entityDataEsAsyncClient;
    private final HealthChecks healthChecks;
    private final KinoticDomainProperties domainProperties;
    private final Vertx vertx;

    @PostConstruct
    public void init(){
        register("elasticsearch",
                 () -> esAsyncClient.cluster()
                                    .health(builder -> builder.index(DomainUtil.INDEX_PREFIX + "application")
                                                              .index(DomainUtil.INDEX_PREFIX + "entity_definition")));
        register("entityDataElasticsearch", () -> entityDataEsAsyncClient.cluster().health());
    }

    private void register(String procedureName, Supplier<CompletableFuture<HealthResponse>> healthRequest){
        // null while the last health request succeeded
        AtomicReference<Throwable> lastError = new AtomicReference<>();

        healthChecks.register(procedureName, future -> {
            Throwable error = lastError.get();
            if(error == null){
                future.complete(Status.OK());
            }else{
                future.fail("Elasticsearch cluster is not healthy. Exception: " + error.getMessage());
            }
        });

        vertx.setPeriodic(domainProperties.getDomain().getElasticHealthCheckInterval().toMillis(),
                          event -> healthRequest.get()
                                                .whenComplete((health, throwable) -> {
                                                    if(throwable != null){
                                                        log.error("Elasticsearch cluster health check {} failed", procedureName, throwable);
                                                    }else{
                                                        log.trace("Elasticsearch cluster health check {} succeeded", procedureName);
                                                    }
                                                    lastError.set(throwable);
                                                }));
    }

}

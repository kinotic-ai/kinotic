package org.kinotic.persistence.internal;

import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * This class is responsible for initializing the Persistence endpoints.
 * Created by Navíd Mitchell 🤪 on 5/30/23.
 */
@Component
@RequiredArgsConstructor
public class PersistenceInitializer {

    private static final Logger log = LoggerFactory.getLogger(PersistenceInitializer.class);
    private final KinoticProperties kinoticProperties;
    private final ElasticsearchAsyncClient esAsyncClient;
    private final HealthChecks healthChecks;
    private final PersistenceProperties properties;
    private final KinoticDomainProperties domainProperties;
    private final Vertx vertx;
    private Throwable lastEsError = null;
    private boolean lastEsStatus = true;

    @PostConstruct
    public void init(){
        int numToDeploy = kinoticProperties.getMaxNumberOfCoresToUse();
        log.info("{} Cores will be used for Persistence Endpoints", numToDeploy);

        healthChecks.register("elasticsearch", future -> {
            if(lastEsStatus){
                future.complete(Status.OK());
            }else{
                future.fail("Elasticsearch cluster is not healthy." + ( lastEsError != null ? " Exception: " + lastEsError.getMessage() : ""));
            }
        });

        vertx.setPeriodic(domainProperties.getDomain().getElasticHealthCheckInterval().toMillis(),
                          event -> esAsyncClient
                                  .cluster()
                                  .health(builder -> builder.index(properties.getIndexPrefix() + "application")
                                                            .index(properties.getIndexPrefix() + "entity_definition"))
                                  .whenComplete((health, throwable) -> {
                                      if(throwable != null){
                                          log.error("Elasticsearch cluster health check failed", throwable);
                                          lastEsStatus = false;
                                          lastEsError = throwable;
                                      }else{
                                          log.trace("Elasticsearch cluster health check succeeded");
                                          lastEsStatus = true;
                                          lastEsError = null;
                                      }
                                  }));
    }

}

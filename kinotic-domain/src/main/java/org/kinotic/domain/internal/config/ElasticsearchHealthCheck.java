package org.kinotic.domain.internal.config;

import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registers the {@code elasticsearch} procedure of the server's {@link HealthChecks}, which reports the
 * Elasticsearch cluster healthy while its last periodic health request for the platform's indices succeeded.
 * Created by Navíd Mitchell 🤪 on 5/30/23.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchHealthCheck {

    private final ElasticsearchAsyncClient esAsyncClient;
    private final HealthChecks healthChecks;
    private final KinoticDomainProperties domainProperties;
    private final Vertx vertx;
    private Throwable lastEsError = null;
    private boolean lastEsStatus = true;

    @PostConstruct
    public void init(){
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
                                  .health(builder -> builder.index(DomainUtil.INDEX_PREFIX + "application")
                                                            .index(DomainUtil.INDEX_PREFIX + "entity_definition"))
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

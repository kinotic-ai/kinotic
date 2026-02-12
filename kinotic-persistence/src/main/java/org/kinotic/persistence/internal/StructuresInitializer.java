package org.kinotic.persistence.internal;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import lombok.RequiredArgsConstructor;
import org.kinotic.rpc.api.config.ContinuumProperties;
import org.kinotic.persistence.api.config.StructuresProperties;
import org.kinotic.persistence.internal.endpoints.StructuresVerticleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * This class is responsible for initializing the structures endpoints.
 * Created by Navíd Mitchell 🤪 on 5/30/23.
 */
@Component
@RequiredArgsConstructor
public class StructuresInitializer {

    private static final Logger log = LoggerFactory.getLogger(StructuresInitializer.class);
    private final ContinuumProperties continuumProperties;
    private final ElasticsearchAsyncClient esAsyncClient;
    private final HealthChecks healthChecks;
    private final StructuresProperties properties;
    private final StructuresVerticleFactory verticleFactory;
    private final Vertx vertx;
    private Throwable lastEsError = null;
    private boolean lastEsStatus = true;

    @PostConstruct
    public void init(){
        int numToDeploy = continuumProperties.getMaxNumberOfCoresToUse();
        log.info("{} Cores will be used for Structures Endpoints", numToDeploy);
        DeploymentOptions options = new DeploymentOptions().setInstances(numToDeploy);

        vertx.deployVerticle(verticleFactory::createOpenApiVerticle, options);

        vertx.deployVerticle(verticleFactory::createGqlVerticle, options);

        if(properties.isEnableStaticFileServer()){// only 1 web server verticle
            vertx.deployVerticle(verticleFactory::createWebServerNextVerticle, new DeploymentOptions());
        }

        healthChecks.register("elasticsearch", future -> {
            if(lastEsStatus){
                future.complete(Status.OK());
            }else{
                future.fail("Elasticsearch cluster is not healthy." + ( lastEsError != null ? " Exception: " + lastEsError.getMessage() : ""));
            }
        });

        vertx.setPeriodic(properties.getElasticHealthCheckInterval().toMillis(),
                          event -> esAsyncClient
                                  .cluster()
                                  .health(builder -> builder.index(properties.getIndexPrefix() + "application")
                                                            .index(properties.getIndexPrefix() + "structure"))
                                  .whenComplete((health, throwable) -> {
                                      if(throwable != null){
                                          log.error("Elasticsearch cluster health check failed", throwable);
                                          lastEsStatus = false;
                                          lastEsError = throwable;
                                      }else{
                                          log.debug("Elasticsearch cluster health check succeeded");
                                          lastEsStatus = true;
                                          lastEsError = null;
                                      }
                                  }));
    }

    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        log.info("Rest API listening on port {}", properties.getOpenApiPort());
        log.info("OpenApi Json available at http://localhost:{}/api-docs/[STRUCTURE APPLICATION]/openapi.json",
                 properties.getOpenApiPort());
        log.info("GraphQL listening on port {}", properties.getGraphqlPort());
        log.info("GraphQL available at http://localhost:{}{}[STRUCTURE APPLICATION]/",
                 properties.getGraphqlPort(),
                 properties.getGraphqlPath());
        if(properties.isEnableStaticFileServer()) {
            log.info("Web Server Next listening on port {}", properties.getWebServerPort());
            log.info("Web Server Next available at http://localhost:{}/", properties.getWebServerPort());
        }
        log.info("Health checks available at http://localhost:{}{}",
                 properties.getWebServerPort(),
                 properties.getHealthCheckPath());
    }

}

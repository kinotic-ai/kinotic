

package org.kinotic.rpc.gateway.internal.endpoints;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.rpc.api.config.KinoticRpcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


/**
 *
 * Created by navid on 2/10/20
 */
@Component
@RequiredArgsConstructor
public class ContinuumGatewayEndpointInitializer {
    private static final Logger log = LoggerFactory.getLogger(ContinuumGatewayEndpointInitializer.class);

    private final ContinuumVertcleFactory continuumVertcleFactory;
    private final KinoticRpcProperties kinoticRpcProperties;
    private final Vertx vertx;

    @PostConstruct
    public void init(){
        // If production deploy one verticle of each per core
        int numToDeploy = kinoticRpcProperties.getMaxNumberOfCoresToUse();
        log.info("{} Cores will be used for Continuum Endpoints", numToDeploy);
        DeploymentOptions options = new DeploymentOptions().setInstances(numToDeploy);

        log.info("Deploying {} Stomp Server Endpoint(s)", numToDeploy);
        vertx.deployVerticle(continuumVertcleFactory::createStompServerVerticle, options);

        log.info("Deploying {} REST Server Endpoint(s)", numToDeploy);
        vertx.deployVerticle(continuumVertcleFactory::createRestServerVerticle, options);
    }

}

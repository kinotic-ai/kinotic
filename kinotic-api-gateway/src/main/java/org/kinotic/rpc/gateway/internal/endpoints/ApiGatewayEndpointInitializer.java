

package org.kinotic.rpc.gateway.internal.endpoints;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.config.KinoticProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


/**
 *
 * Created by navid on 2/10/20
 */
@Component
@RequiredArgsConstructor
public class ApiGatewayEndpointInitializer {
    private static final Logger log = LoggerFactory.getLogger(ApiGatewayEndpointInitializer.class);

    private final ApiGatewayVertcleFactory apiGatewayVertcleFactory;
    private final KinoticProperties kinoticProperties;
    private final Vertx vertx;

    @PostConstruct
    public void init(){
        int numToDeploy = kinoticProperties.getMaxNumberOfCoresToUse();
        log.info("{} Cores will be used for Kinoitc Endpoints", numToDeploy);
        DeploymentOptions options = new DeploymentOptions().setInstances(numToDeploy);

        log.info("Deploying {} Stomp Server Endpoint(s)", numToDeploy);
        vertx.deployVerticle(apiGatewayVertcleFactory::createApiGatewayVerticle, options);
    }

}

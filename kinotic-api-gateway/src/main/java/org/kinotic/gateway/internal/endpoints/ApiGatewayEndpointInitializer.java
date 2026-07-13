

package org.kinotic.gateway.internal.endpoints;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.stomp.lite.StompServerOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.gateway.api.config.ApiGatewayProperties;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
    private final ApiGatewayProperties apiGatewayProperties;
    private final Vertx vertx;

    @PostConstruct
    public void init(){
        int numToDeploy = kinoticProperties.getMaxNumberOfCoresToUse();
        DeploymentOptions options = new DeploymentOptions().setInstances(numToDeploy);

        vertx.deployVerticle(apiGatewayVertcleFactory::createApiGatewayVerticle, options);

        if (apiGatewayProperties.getWebServer().isEnabled()) {
            vertx.deployVerticle(apiGatewayVertcleFactory::createWebServerVerticle, new DeploymentOptions());
        }
    }

    @EventListener
    public void onApplicationReadyEvent(ApplicationReadyEvent event) {
        int numToDeploy = kinoticProperties.getMaxNumberOfCoresToUse();
        log.info("Deploying {} API Gateway Endpoint(s), 1 per core", numToDeploy);


        StompServerOptions stompServerOptions = apiGatewayProperties.getStomp();
        log.info("Deploying API Server at http{}://{}", apiGatewayProperties.getSsl().isEnabled() ? "s" : "", stompServerOptions.getPort());

        if (apiGatewayProperties.getWebServer().isEnabled()) {
            log.info("Deploying static web server on port {}", apiGatewayProperties.getWebServer().getPort());
        }
    }

}

package org.kinotic.rpc.gateway.internal.endpoints;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.stomp.lite.StompServerHandlerFactory;
import io.vertx.ext.stomp.lite.StompServerOptions;
import io.vertx.ext.stomp.lite.StompServerVerticle;
import io.vertx.ext.stomp.lite.StompServerVerticleFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import org.kinotic.rpc.api.config.ContinuumProperties;
import org.kinotic.rpc.api.security.SecurityService;
import org.kinotic.rpc.api.event.EventBusService;
import org.kinotic.rpc.gateway.api.config.ContinuumGatewayProperties;
import org.kinotic.rpc.gateway.internal.endpoints.rest.RestServerVerticle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Provides a factory for creating continuum end point verticles.
 * Created by Navíd Mitchell 🤪 on 3/6/24.
 */
@Component
public class ContinuumVertcleFactory {

    private final ContinuumProperties continuumProperties;
    private final ContinuumGatewayProperties gatewayProperties;
    private final StompServerHandlerFactory stompServerHandlerFactory;
    private final EventBusService eventService;
    private final SecurityService securityService;
    private final JsonMapper jsonMapper;
    private final Vertx vertx;

    public ContinuumVertcleFactory(ContinuumProperties continuumProperties,
                                   ContinuumGatewayProperties gatewayProperties,
                                   StompServerHandlerFactory stompServerHandlerFactory,
                                   EventBusService eventService,
                                   JsonMapper jsonMapper,
                                   Vertx vertx,
                                   @Autowired(required = false) SecurityService securityService) {
        this.continuumProperties = continuumProperties;
        this.gatewayProperties = gatewayProperties;
        this.stompServerHandlerFactory = stompServerHandlerFactory;
        this.eventService = eventService;
        this.jsonMapper = jsonMapper;
        this.vertx = vertx;
        this.securityService = securityService;
    }

    public StompServerVerticle createStompServerVerticle(){
        Router router = Router.router(vertx);
        router.route().handler(StaticHandler.create("continuum-gateway-static"));

        // FIXME: check CORS, see if it is protected or actually allowing any..?
        StompServerOptions stompServerOptions = gatewayProperties.getStomp();

        // we override the body length with the continuum properties
        stompServerOptions.setMaxBodyLength(continuumProperties.getMaxEventPayloadSize());
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setWebSocketSubProtocols(List.of("v12.stomp"));
        serverOptions.setMaxWebSocketFrameSize(continuumProperties.getMaxEventPayloadSize());

        return StompServerVerticleFactory.create(serverOptions, stompServerOptions, stompServerHandlerFactory, router);
    }

    public RestServerVerticle createRestServerVerticle(){
       return new RestServerVerticle(gatewayProperties, eventService, securityService, jsonMapper);
    }

}

package org.kinotic.rpc.gateway.internal.endpoints;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.stomp.lite.StompServerHandlerFactory;
import io.vertx.ext.stomp.lite.StompServerOptions;
import io.vertx.ext.stomp.lite.StompServerVerticle;
import io.vertx.ext.stomp.lite.StompServerVerticleFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.config.SslHelper;
import org.kinotic.rpc.gateway.api.config.KinoticRpcGatewayProperties;
import org.kinotic.rpc.gateway.api.config.RpcGatewayProperties;
import org.kinotic.rpc.gateway.internal.endpoints.rest.SignUpHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provides a factory for creating continuum end point verticles.
 * Created by Navíd Mitchell 🤪 on 3/6/24.
 */
@Component
public class RpcGatewayVertcleFactory {

    private final KinoticProperties kinoticProperties;
    private final RpcGatewayProperties gatewayProperties;
    private final StompServerHandlerFactory stompServerHandlerFactory;
    private final SignUpHandler signUpHandler;
    private final Vertx vertx;

    public RpcGatewayVertcleFactory(KinoticProperties kinoticProperties,
                                    KinoticRpcGatewayProperties kinoticRpcGatewayProperties,
                                    StompServerHandlerFactory stompServerHandlerFactory,
                                    SignUpHandler signUpHandler,
                                    Vertx vertx) {
        this.kinoticProperties = kinoticProperties;
        this.gatewayProperties = kinoticRpcGatewayProperties.getRpcGateway();
        this.stompServerHandlerFactory = stompServerHandlerFactory;
        this.signUpHandler = signUpHandler;
        this.vertx = vertx;
    }

    public StompServerVerticle createStompServerVerticle(){
        Router router = Router.router(vertx);

        // Mount REST endpoints before the static handler catch-all
        signUpHandler.mountRoutes(router);

        router.route().handler(StaticHandler.create("rpc-gateway-static"));

        // FIXME: check CORS, see if it is protected or actually allowing any..?
        StompServerOptions stompServerOptions = gatewayProperties.getStomp();

        // we override the body length with the continuum properties
        stompServerOptions.setMaxBodyLength(kinoticProperties.getMaxEventPayloadSize());
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.setWebSocketSubProtocols(List.of("v12.stomp"));
        serverOptions.setMaxWebSocketFrameSize(kinoticProperties.getMaxEventPayloadSize());
        SslHelper.applySsl(serverOptions, kinoticProperties.getSsl());

        return StompServerVerticleFactory.create(serverOptions, stompServerOptions, stompServerHandlerFactory, router);
    }

}

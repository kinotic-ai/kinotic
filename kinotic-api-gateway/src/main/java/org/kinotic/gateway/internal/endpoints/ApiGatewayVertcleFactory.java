package org.kinotic.gateway.internal.endpoints;

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
import org.kinotic.gateway.api.config.ApiGatewayProperties;
import org.kinotic.gateway.api.config.KinoticApiGatewayProperties;
import org.kinotic.gateway.internal.endpoints.rest.OidcLoginHandler;
import org.kinotic.gateway.internal.endpoints.rest.OidcSignupHandler;
import org.kinotic.gateway.internal.endpoints.rest.SignUpHandler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Provides a factory for creating continuum end point verticles.
 * Created by Navíd Mitchell 🤪 on 3/6/24.
 */
@Component
public class ApiGatewayVertcleFactory {

    private final KinoticProperties kinoticProperties;
    private final ApiGatewayProperties gatewayProperties;
    private final StompServerHandlerFactory stompServerHandlerFactory;
    private final SignUpHandler signUpHandler;
    private final OidcLoginHandler oidcLoginHandler;
    private final OidcSignupHandler oidcSignupHandler;
    private final Vertx vertx;

    public ApiGatewayVertcleFactory(KinoticProperties kinoticProperties,
                                    KinoticApiGatewayProperties kinoticApiGatewayProperties,
                                    StompServerHandlerFactory stompServerHandlerFactory,
                                    SignUpHandler signUpHandler,
                                    OidcLoginHandler oidcLoginHandler,
                                    OidcSignupHandler oidcSignupHandler,
                                    Vertx vertx) {
        this.kinoticProperties = kinoticProperties;
        this.gatewayProperties = kinoticApiGatewayProperties.getRpcGateway();
        this.stompServerHandlerFactory = stompServerHandlerFactory;
        this.signUpHandler = signUpHandler;
        this.oidcLoginHandler = oidcLoginHandler;
        this.oidcSignupHandler = oidcSignupHandler;
        this.vertx = vertx;
    }

    public StompServerVerticle createApiGatewayVerticle(){
        Router router = Router.router(vertx);

        // Mount REST endpoints before the static handler catch-all
        signUpHandler.mountRoutes(router);
        oidcLoginHandler.mountRoutes(router);
        oidcSignupHandler.mountRoutes(router);

        router.route().handler(StaticHandler.create("api-gateway-static"));

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

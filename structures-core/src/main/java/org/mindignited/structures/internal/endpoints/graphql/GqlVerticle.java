package org.mindignited.structures.internal.endpoints.graphql;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import lombok.RequiredArgsConstructor;
import org.mindignited.continuum.api.security.SecurityService;
import org.mindignited.continuum.gateway.api.security.AuthenticationHandler;
import org.mindignited.structures.api.config.StructuresProperties;
import org.mindignited.structures.internal.utils.VertxWebUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Navíd Mitchell 🤪 on 6/7/23.
 */
@RequiredArgsConstructor
public class GqlVerticle extends VerticleBase {

    public static final String APPLICATION_PATH_PARAMETER = "structureApplication";

    private static final Logger log = LoggerFactory.getLogger(GqlVerticle.class);
    private final DelegatingGqlHandler gqlHandler;
    private final StructuresProperties properties;
    private final SecurityService securityService;
    private HttpServer server;


    @Override
    public Future<?> start() throws Exception {
        HttpServerOptions options = new HttpServerOptions();
        options.setMaxHeaderSize(properties.getMaxHttpHeaderSize());
        server = vertx.createHttpServer(options);

        Router router = Router.router(vertx);

        router.route().failureHandler(VertxWebUtil.createExceptionConvertingFailureHandler());

        String allowedOriginPattern = properties.getCorsAllowedOriginPattern();
        if ("*".equals(allowedOriginPattern)) {
            allowedOriginPattern = ".*";
        }

        CorsHandler corsHandler = CorsHandler.create()
                                             .addOriginWithRegex(allowedOriginPattern)
                                             .allowedHeaders(properties.getCorsAllowedHeaders());

        if(properties.getCorsAllowCredentials() != null){
            corsHandler.allowCredentials(properties.getCorsAllowCredentials());
        }

        router.route().handler(corsHandler);

        if(securityService !=null){
            router.route().handler(new AuthenticationHandler(securityService, vertx));
        }

        router.post(properties.getGraphqlPath()+":"+APPLICATION_PATH_PARAMETER)
              .consumes("application/json")
              .consumes("application/graphql")
              .produces("application/json")
              .handler(BodyHandler.create(false)
                                  .setBodyLimit(properties.getMaxHttpBodySize()))
              .handler(gqlHandler);

        // Begin listening for requests
        return server.requestHandler(router)
                     .listen(properties.getGraphqlPort());
    }

    @Override
    public Future<?> stop() throws Exception {
        return server.close();
    }

}

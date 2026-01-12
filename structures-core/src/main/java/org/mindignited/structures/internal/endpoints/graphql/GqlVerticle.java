package org.mindignited.structures.internal.endpoints.graphql;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
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
public class GqlVerticle extends AbstractVerticle {

    public static final String APPLICATION_PATH_PARAMETER = "structureApplication";

    private static final Logger log = LoggerFactory.getLogger(GqlVerticle.class);
    private final DelegatingGqlHandler gqlHandler;
    private final StructuresProperties properties;
    private final SecurityService securityService;
    private HttpServer server;


    @Override
    public void start(Promise<Void> startPromise) {
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
                                             .addRelativeOrigin(allowedOriginPattern)
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
        server.requestHandler(router)
              .listen(properties.getGraphqlPort(), ar -> {
                  if (ar.succeeded()) {
                      log.info("GraphQL Started Listener on Thread {}", Thread.currentThread().getName());
                      startPromise.complete();
                  } else {
                      startPromise.fail(ar.cause());
                  }
              });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        server.close(stopPromise);
    }
}

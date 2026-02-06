package org.kinotic.structures.internal.endpoints.openapi;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import org.kinotic.structures.api.config.StructuresProperties;
import org.kinotic.structures.api.domain.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We have one OpenApi spec for all {@link Structure}'s in a application. But this handles all applications and all structures.
 * Created by Navíd Mitchell 🤪 on 5/29/23.
 */
@RequiredArgsConstructor
public class OpenApiVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(OpenApiVerticle.class);

    private final StructuresProperties properties;
    private final Router router;
    private HttpServer server;

    public void start(Promise<Void> startPromise) {
        HttpServerOptions options = new HttpServerOptions();
        options.setMaxHeaderSize(properties.getMaxHttpHeaderSize());
        server = vertx.createHttpServer(options);

        // Begin listening for requests
        server.requestHandler(router)
              .listen(properties.getOpenApiPort(), ar -> {
            if (ar.succeeded()) {
                log.info("OpenApi Started Listener on Thread {}", Thread.currentThread().getName());
                startPromise.complete();
            } else {
                startPromise.fail(ar.cause());
            }
        });
    }

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception {
        server.close(stopPromise);
    }

}

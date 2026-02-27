package org.kinotic.persistence.internal.endpoints.openapi;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import org.kinotic.persistence.api.config.PersistenceProperties;
import org.kinotic.persistence.api.model.EntityDefinition;

/**
 * We have one OpenApi spec for all {@link EntityDefinition}'s in a application. But this handles all applications and all structures.
 * Created by Navíd Mitchell 🤪 on 5/29/23.
 */
@RequiredArgsConstructor
public class OpenApiVerticle extends VerticleBase {

    private final PersistenceProperties properties;
    private final Router router;
    private HttpServer server;


    @Override
    public Future<?> start() throws Exception {
        HttpServerOptions options = new HttpServerOptions();
        options.setMaxHeaderSize(properties.getMaxHttpHeaderSize());
        server = vertx.createHttpServer(options);

        // Begin listening for requests
        return server.requestHandler(router)
                     .listen(properties.getOpenApiPort());
    }

    @Override
    public Future<?> stop() throws Exception {
        return server.close();
    }


}

package org.kinotic.gateway.internal.endpoints;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.SslHelper;
import org.kinotic.core.api.config.SslProperties;
import org.kinotic.gateway.api.config.WebServerProperties;
import io.vertx.ext.web.handler.StaticHandler;

/**
 * Serves the kinotic-frontend SPA from {@code resources/webroot} on a dedicated port.
 * Only deployed when {@link WebServerProperties#isEnabled()} is true — KinD and Azure
 * skip this and host the SPA outside the cluster.
 * <p>
 * CORS and the health-check endpoint live on the api-gateway router (port 58503), not
 * here, so probes and cross-origin API calls keep working when this verticle is off.
 */
@Slf4j
@RequiredArgsConstructor
public class WebServerVerticle extends VerticleBase {

    private final WebServerProperties properties;
    private final SslProperties sslProperties;
    private HttpServer server;

    @Override
    public Future<?> start() throws Exception {
        HttpServerOptions serverOptions = new HttpServerOptions();
        SslHelper.applySsl(serverOptions, sslProperties);
        server = vertx.createHttpServer(serverOptions);

        Router router = Router.router(vertx);

        router.route().handler(StaticHandler.create("webroot"));

        // SPA fallback — serve index.html for any unmatched GET so client-side routing
        // survives a refresh on a deep link.
        router.get("/*").handler(ctx -> ctx.response().sendFile("webroot/index.html"));

        return server.requestHandler(router).listen(properties.getPort());
    }

    @Override
    public Future<?> stop() throws Exception {
        return server.close();
    }
}

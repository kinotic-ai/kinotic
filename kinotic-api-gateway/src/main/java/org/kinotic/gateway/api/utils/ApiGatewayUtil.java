package org.kinotic.gateway.api.utils;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.web.handler.CorsHandler;
import org.kinotic.gateway.api.config.CorsProperties;
import org.kinotic.gateway.api.config.SslProperties;

import java.util.Set;

/**
 * Helpers for configuring the Vert.x HTTP servers fronted by the api-gateway: CORS handling
 * and PEM-based TLS. Applied to every browser-facing route (STOMP, static web, and the
 * openapi/graphql routes mounted by other modules).
 */
public final class ApiGatewayUtil {

    /**
     * The HTTP methods kinotic's HTTP routes accept. Pinned here rather than exposed on
     * {@link CorsProperties} because Vert.x's {@code CorsHandler} allows no methods by default
     * and a misconfigured deployment value would silently break every non-GET endpoint. Update
     * this set if a new HTTP verb is introduced anywhere in the codebase.
     */
    private static final Set<HttpMethod> ALLOWED_METHODS = Set.of(HttpMethod.GET,
                                                                  HttpMethod.POST,
                                                                  HttpMethod.PUT,
                                                                  HttpMethod.PATCH,
                                                                  HttpMethod.DELETE,
                                                                  HttpMethod.OPTIONS);

    private ApiGatewayUtil() {}

    public static CorsHandler createCorsHandler(CorsProperties properties) {
        String pattern = properties.getAllowedOriginPattern();
        if ("*".equals(pattern)) {
            pattern = ".*";
        }
        CorsHandler corsHandler = CorsHandler.create()
                                             .addOriginWithRegex(pattern)
                                             .allowedMethods(ALLOWED_METHODS)
                                             .allowedHeaders(properties.getAllowedHeaders());
        if (properties.getAllowCredentials() != null) {
            corsHandler.allowCredentials(properties.getAllowCredentials());
        }
        return corsHandler;
    }

    /**
     * If SSL is enabled in the given properties, configures the server options
     * with PEM-based TLS. Otherwise leaves the options unchanged.
     *
     * @param options the server options to configure
     * @param ssl     the SSL properties
     * @return the same options instance for chaining
     */
    public static HttpServerOptions applySsl(HttpServerOptions options, SslProperties ssl) {
        if (ssl != null && ssl.isEnabled()) {
            options.setSsl(true)
                   .setKeyCertOptions(new PemKeyCertOptions()
                           .setCertPath(ssl.getCertPath())
                           .setKeyPath(ssl.getKeyPath()));
        }
        return options;
    }
}

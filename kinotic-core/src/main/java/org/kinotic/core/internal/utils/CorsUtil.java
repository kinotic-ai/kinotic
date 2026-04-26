package org.kinotic.core.internal.utils;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.handler.CorsHandler;
import org.kinotic.core.api.config.CorsProperties;

import java.util.Set;

public final class CorsUtil {

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

    private CorsUtil() {}

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
}

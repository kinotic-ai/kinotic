package org.kinotic.core.api.config;

import io.vertx.ext.web.handler.CorsHandler;

public final class CorsHelper {

    private CorsHelper() {}

    public static CorsHandler createCorsHandler(CorsProperties properties) {
        String pattern = properties.getAllowedOriginPattern();
        if ("*".equals(pattern)) {
            pattern = ".*";
        }
        CorsHandler corsHandler = CorsHandler.create()
                                             .addOriginWithRegex(pattern)
                                             .allowedHeaders(properties.getAllowedHeaders());
        if (properties.getAllowCredentials() != null) {
            corsHandler.allowCredentials(properties.getAllowCredentials());
        }
        return corsHandler;
    }
}

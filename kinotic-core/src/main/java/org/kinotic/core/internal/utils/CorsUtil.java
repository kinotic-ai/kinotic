package org.kinotic.core.internal.utils;

import io.vertx.ext.web.handler.CorsHandler;
import org.kinotic.core.api.config.CorsProperties;

public final class CorsUtil {

    private CorsUtil() {}

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

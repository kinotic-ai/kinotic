package org.kinotic.core.api.config;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.PemKeyCertOptions;

/**
 * Applies SSL/TLS configuration to Vert.x {@link HttpServerOptions}
 * based on {@link SslProperties}.
 *
 * Created by Claude on 4/4/26.
 */
public final class SslHelper {

    private SslHelper() {}

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

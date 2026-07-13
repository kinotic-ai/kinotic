

package org.kinotic.gateway.api.config;

import org.kinotic.core.api.config.KinoticProperties;

import io.vertx.ext.stomp.lite.StompServerOptions;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by Navid Mitchell on 7/19/17.
 */
@Getter
@Setter
public class ApiGatewayProperties {
    public static long DEFAULT_SESSION_TIMEOUT = 1000 * 60 * 30;
    public static int DEFAULT_STOMP_PORT = 58503;
    public static String DEFAULT_STOMP_WEBSOCKET_PATH = "/v1";

    /**
     * How long a session should last in milliseconds.
     */
    private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    /**
     * Stomp server configuration.
     */
    private StompServerOptions stomp;

    /**
     * Static-file web server configuration. Disabled in KinD/Azure where the SPA
     * is hosted outside the cluster.
     */
    private WebServerProperties webServer = new WebServerProperties();

    /**
     * CORS configuration applied to all Vert.x HTTP servers that expose browser-facing routes.
     */
    private CorsProperties cors = new CorsProperties();

    /**
     * SSL/TLS configuration for all Vert.x HTTP servers.
     */
    private SslProperties ssl = new SslProperties();

    public ApiGatewayProperties(KinoticProperties kinoticProperties) {
        stomp = new StompServerOptions()
                .setPort(DEFAULT_STOMP_PORT)
                .setWebsocketPath(DEFAULT_STOMP_WEBSOCKET_PATH)
                .setDebugEnabled(kinoticProperties.isDebug());
    }

}

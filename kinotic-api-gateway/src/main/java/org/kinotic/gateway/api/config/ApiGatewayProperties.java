

package org.kinotic.gateway.api.config;

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

    /**
     * How long a session should last in milliseconds.
     */
    private long sessionTimeout = DEFAULT_SESSION_TIMEOUT;

    /**
     * Port the STOMP server listens on.
     */
    private int stompPort = DEFAULT_STOMP_PORT;

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

}

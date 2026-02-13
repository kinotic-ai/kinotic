

package org.kinotic.rpc.gateway.api.config;

import io.vertx.ext.stomp.lite.StompServerOptions;
import lombok.Getter;
import lombok.Setter;
import org.kinotic.boot.api.config.KinoticProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Created by Navid Mitchell on 7/19/17.
 */
@Component
@ConfigurationProperties(prefix = "continuum-gateway")
@Getter
@Setter
public class ContinuumGatewayProperties {
    public static int DEFAULT_STOMP_PORT = 58503;
    public static String DEFAULT_STOMP_WEBSOCKET_PATH = "/v1";
    public static int DEFAULT_REST_PORT = 58504;
    public static String DEFAULT_REST_PATH = "/api";
    public static long DEFAULT_REST_BODY_LIMIT_SIZE = 2048;

    private StompServerOptions stomp;

    private final ContinuumRestServerProperties rest = new ContinuumRestServerProperties();

    /**
     * Denotes if the CLI connections should be enabled or not
     * True if CLI connections should be enabled false if not
     */
    private boolean enableCLIConnections = true;

    public ContinuumGatewayProperties(KinoticProperties kinoticProperties) {
        stomp = new StompServerOptions()
                .setPort(DEFAULT_STOMP_PORT)
                .setWebsocketPath(DEFAULT_STOMP_WEBSOCKET_PATH)
                .setDebugEnabled(kinoticProperties.isDebug());
    }
}

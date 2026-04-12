

package org.kinotic.rpc.gateway.api.config;

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
    public static int DEFAULT_STOMP_PORT = 58503;
    public static String DEFAULT_STOMP_WEBSOCKET_PATH = "/v1";

    private StompServerOptions stomp;

    /**
     * Denotes if the CLI connections should be enabled or not
     * True if CLI connections should be enabled false if not
     */
    private boolean enableCLIConnections = true;

    public ApiGatewayProperties(KinoticProperties kinoticProperties) {
        stomp = new StompServerOptions()
                .setPort(DEFAULT_STOMP_PORT)
                .setWebsocketPath(DEFAULT_STOMP_WEBSOCKET_PATH)
                .setDebugEnabled(kinoticProperties.isDebug());
    }
}

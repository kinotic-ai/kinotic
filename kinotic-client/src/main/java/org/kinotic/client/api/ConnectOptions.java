package org.kinotic.client.api;

import io.vertx.core.net.ClientSSLOptions;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Options for {@link KinoticConnection#connect(ConnectOptions)}.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Getter
@Setter
@Accessors(chain = true)
public class ConnectOptions {

    /**
     * The gateway to connect to, as a {@code ws://} or {@code wss://} URL naming its host and, unless the scheme's
     * default applies, its port, such as {@code wss://api.kinotic.ai}. The client appends the gateway's STOMP
     * endpoint path itself.
     */
    private String serverUrl;

    /**
     * The headers the WebSocket upgrade request carries, which the gateway hands to its {@code SecurityService}
     * as the connection's authentication info. The client never interprets them; they are sent on every connect
     * and reconnect.
     */
    private Map<String, String> authHeaders = new HashMap<>();

    /**
     * The TLS options of a {@code wss://} connection: trust and key material, or trust-all for a development
     * server. {@code null} applies the JDK defaults.
     */
    private ClientSSLOptions sslOptions;

    /**
     * The largest reply the client accepts, in bytes; a larger frame ends the connection. Defaults to the gateway's
     * own default event payload size.
     */
    private int maxFrameSize = new KinoticProperties().getMaxEventPayloadSize();

    /**
     * How the connection retries connecting.
     */
    private ReconnectOptions reconnect = new ReconnectOptions();

}

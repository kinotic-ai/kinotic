package org.kinotic.client.api;

import io.vertx.core.net.ClientSSLOptions;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Options for {@link KinoticConnectionFactory#connect(ConnectOptions)}.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ConnectOptions {

    /**
     * The gateway to connect to, as a {@code ws://} or {@code wss://} URL naming its host and, unless the scheme's
     * default applies, its port, such as {@code wss://api.kinotic.ai}. The client appends the gateway's STOMP
     * endpoint path itself.
     */
    private String serverUrl;

    /**
     * The headers the WebSocket upgrade request carries, which the gateway hands to its {@code SecurityService}
     * as the connection's authentication info. They are sent on every connect and reconnect.
     */
    private Map<String, String> authHeaders = new HashMap<>();

    /**
     * The TLS options of a {@code wss://} connection: trust and key material, or trust-all for a development
     * server. {@code null} applies the JDK defaults.
     */
    private ClientSSLOptions sslOptions;

    /**
     * The size a reply's body must stay under, in bytes; a reply whose body reaches it ends the connection. A request
     * larger than the gateway's {@code kinotic.maxEventPayloadSize} is refused by the gateway. Defaults to that
     * property's default.
     */
    private int maxBodyLength = KinoticProperties.DEFAULT_MAX_EVENT_PAYLOAD_SIZE;

    /**
     * The largest WebSocket frame the client accepts, in bytes; a larger one ends the connection. The gateway writes a
     * reply in WebSocket frames of at most its {@code kinotic.maxEventPayloadSize}, so this is at least that. Defaults
     * to that property's default.
     */
    private int maxWebSocketFrameSize = KinoticProperties.DEFAULT_MAX_EVENT_PAYLOAD_SIZE;

    /**
     * How the connection retries connecting.
     */
    private ReconnectOptions reconnect = new ReconnectOptions();

    /**
     * A copy of the given options, sharing nothing with them: a change to the original afterwards does not reach the
     * copy.
     * @param other the options to copy
     */
    public ConnectOptions(ConnectOptions other) {
        this.serverUrl = other.serverUrl;
        this.authHeaders = other.authHeaders == null ? null : new HashMap<>(other.authHeaders);
        this.sslOptions = other.sslOptions == null ? null : other.sslOptions.copy();
        this.maxBodyLength = other.maxBodyLength;
        this.maxWebSocketFrameSize = other.maxWebSocketFrameSize;
        this.reconnect = other.reconnect == null ? null : new ReconnectOptions(other.reconnect);
    }

}

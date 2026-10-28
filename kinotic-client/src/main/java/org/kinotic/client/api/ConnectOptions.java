package org.kinotic.client.api;

import io.opentelemetry.api.OpenTelemetry;
import io.vertx.core.net.ClientSSLOptions;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.config.KinoticProperties;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

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
     * as the connection's authentication info. They are sent on every connect and reconnect.
     */
    private Map<String, String> authHeaders = new HashMap<>();

    /**
     * The TLS options of a {@code wss://} connection: trust and key material, or trust-all for a development
     * server. {@code null} applies the JDK defaults.
     */
    private ClientSSLOptions sslOptions;

    /**
     * The largest STOMP frame the connection carries in either direction, in bytes: a larger reply ends the
     * connection, and a request is written in WebSocket frames up to it, so it must not exceed the gateway's
     * {@code kinotic.maxEventPayloadSize}. Defaults to that property's default.
     */
    private int maxFrameSize = new KinoticProperties().getMaxEventPayloadSize();

    /**
     * How the connection retries connecting.
     */
    private ReconnectOptions reconnect = new ReconnectOptions();

    /**
     * Customizes the Jackson mapper the proxies convert arguments and replies with. The mapper starts as the one a
     * cluster member converts with: the Vert.x types in their Vert.x wire format, the platform's own value types, and
     * no constructor parameter detection. A gateway's own mapper may carry more, such as the module a Kinotic OS
     * server registers for its domain types; the modules the called services' types need are added here.
     * {@code null} leaves the mapper as it starts.
     */
    private Consumer<JsonMapper.Builder> jsonMapperCustomizer;

    /**
     * Traces the proxies' invocations, continued by the services they reach. {@link OpenTelemetry#noop()} by
     * default; a program with a configured SDK passes it here.
     */
    private OpenTelemetry openTelemetry = OpenTelemetry.noop();

}

package org.kinotic.client.internal.api;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.UpgradeRejectedException;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.internal.ContextInternal;
import io.vertx.ext.stomp.lite.client.StompClient;
import io.vertx.ext.stomp.lite.client.StompClientConnection;
import io.vertx.ext.stomp.lite.client.StompClientOptions;
import io.vertx.ext.stomp.lite.client.StompErrorException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.client.api.ConnectOptions;
import org.kinotic.client.api.ConnectionState;
import org.kinotic.client.api.KinoticConnection;
import org.kinotic.client.api.ReconnectOptions;
import org.kinotic.core.api.RpcServiceProxyFactory;
import org.kinotic.core.api.RpcServiceProxyHandle;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.SessionKeepAliveMode;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.service.ServiceIdentifier;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default {@link KinoticConnection}: a STOMP connection to the gateway's WebSocket endpoint, kept alive across
 * losses with the retry policy of its {@link ReconnectOptions}, and the service proxies minted over it. Everything it
 * does runs on one context of the Vert.x instance it is given.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Slf4j
public class DefaultKinoticConnection implements KinoticConnection {

    /**
     * The gateway's STOMP endpoint, the same path the TypeScript client connects to.
     */
    private static final String GATEWAY_PATH = "/api/v1";

    /**
     * Names this process in the reply destinations of its proxies.
     */
    private static final String NODE_NAME = "kinotic-client";

    private static final Set<String> WEBSOCKET_SCHEMES = Set.of("ws", "wss");
    private static final int HTTP_UNAUTHORIZED = 401;

    // a client outside a browser presents no session cookie, so the gateway keeps nothing for it between connections
    private static final Map<String, String> CONNECT_HEADERS = Map.of(EventConstants.SESSION_KEEP_ALIVE_HEADER,
                                                                      SessionKeepAliveMode.NONE.name());

    private final ConnectOptions options;
    private final Vertx vertx;
    private final ContextInternal context;
    private final StompRpcTransport transport;
    private final ConnectionLivenessWatcher livenessWatcher;
    private final RpcServiceProxyFactory proxyFactory;
    private final ObjectReader connectedInfoReader;
    private final Set<RpcServiceProxyHandle<?>> handles = ConcurrentHashMap.newKeySet();
    private volatile ConnectionState state;
    private volatile Handler<ConnectionState> stateHandler;
    private volatile ConnectedInfo connectedInfo;
    // touched only on the context
    private Future<Void> closed;
    private StompClient stompClient;
    private StompClientConnection current;
    private String currentConnectionId;
    private int connections = 0;
    private int failedAttempts = 0;
    private long reconnectTimer = -1;
    private Promise<KinoticConnection> initialConnect;
    // written on the context, read by serviceProxy() from any thread
    private volatile boolean closing = false;

    /**
     * Connects to the gateway the options name.
     * @param vertx the Vert.x instance the connection runs on
     * @param options where to connect and how to authenticate
     * @return a future completed with the connection once the gateway accepted it
     */
    public static Future<KinoticConnection> connect(Vertx vertx, ConnectOptions options) {
        Validate.notNull(vertx, "vertx must not be null");
        Validate.notNull(options, "options must not be null");
        Validate.notBlank(options.getServerUrl(), "serverUrl must not be blank");
        Validate.notNull(options.getAuthHeaders(), "authHeaders must not be null");
        Validate.notNull(options.getReconnect(), "reconnect must not be null");
        URI serverUri = URI.create(options.getServerUrl());
        Validate.isTrue(serverUri.getScheme() != null && WEBSOCKET_SCHEMES.contains(serverUri.getScheme()),
                        "serverUrl must be a ws:// or wss:// URL but was '%s'", options.getServerUrl());
        Validate.isTrue(serverUri.getHost() != null, "serverUrl must name a host but was '%s'", options.getServerUrl());
        Validate.isTrue(!serverUri.getPath().replaceAll("/+$", "").endsWith(GATEWAY_PATH),
                        "serverUrl names the gateway without its %s path, which the client appends itself", GATEWAY_PATH);
        Validate.isTrue(serverUri.getQuery() == null && serverUri.getFragment() == null,
                        "serverUrl must carry no query or fragment but was '%s'", options.getServerUrl());
        Validate.notNull(options.getOpenTelemetry(), "openTelemetry must not be null");
        Validate.isTrue(options.getAuthHeaders().keySet().stream().noneMatch(Objects::isNull), "authHeaders must not hold a null key");
        Validate.isTrue(options.getAuthHeaders().values().stream().noneMatch(Objects::isNull), "authHeaders must not hold a null value");
        Validate.isTrue(options.getMaxBodyLength() >= 1, "maxBodyLength must be at least 1 byte");
        Validate.isTrue(options.getMaxWebSocketFrameSize() >= 1, "maxWebSocketFrameSize must be at least 1 byte");
        ReconnectOptions reconnect = options.getReconnect();
        Validate.isTrue(reconnect.getMaxAttempts() >= 0, "reconnect.maxAttempts must not be negative");
        // Vert.x schedules no timer under a millisecond, so the delays and the timeout are at least that
        validateAtLeastOneMillisecond(reconnect.getInitialDelay(), "reconnect.initialDelay");
        validateAtLeastOneMillisecond(reconnect.getMaxDelay(), "reconnect.maxDelay");
        validateAtLeastOneMillisecond(reconnect.getConnectionTimeout(), "reconnect.connectionTimeout");
        Validate.isTrue(reconnect.getMaxJitter() != null && !reconnect.getMaxJitter().isNegative(),
                        "reconnect.maxJitter must not be negative");
        return new DefaultKinoticConnection(vertx, options).start();
    }

    private static void validateAtLeastOneMillisecond(Duration duration, String name) {
        Validate.isTrue(duration != null && duration.toMillis() >= 1, "%s must be at least 1 ms", name);
    }

    private DefaultKinoticConnection(Vertx vertx, ConnectOptions options) {
        this.options = options;
        // the gateway's participant model is not on this classpath, so a participant is read as what every participant has
        SimpleModule participantModule = new SimpleModule("KinoticClientModule")
                .addAbstractTypeMapping(Participant.class, ConnectedParticipant.class);
        // registered before the customizer, whose own mapping of a participant then takes precedence
        JsonMapper.Builder jsonMapperBuilder = RpcServiceProxyFactory.jsonMapperBuilder().addModule(participantModule);
        if (options.getJsonMapperCustomizer() != null) {
            options.getJsonMapperCustomizer().accept(jsonMapperBuilder);
        }
        JsonMapper jsonMapper = jsonMapperBuilder.build();
        this.vertx = vertx;
        // a duplicate starts with empty locals, so the connection carries nothing of the context connect() was
        // called on and shares its context with no other connection
        this.context = ((ContextInternal) vertx.getOrCreateContext()).duplicate();
        this.livenessWatcher = new ConnectionLivenessWatcher(vertx);
        this.transport = new StompRpcTransport(context, options.getServerUrl(), NODE_NAME,
                                               options.getReconnect().getConnectionTimeout().toMillis());
        KinoticProperties properties = new KinoticProperties().setMaxEventPayloadSize(options.getMaxBodyLength());
        this.proxyFactory = new RpcServiceProxyFactory(transport, livenessWatcher, vertx, options.getOpenTelemetry(),
                                                       properties, jsonMapper, NODE_NAME);
        // the CONNECTED frame is the client's own protocol, read by a mapper the customizer never touched
        this.connectedInfoReader = RpcServiceProxyFactory.jsonMapperBuilder()
                                                         .addModule(participantModule)
                                                         .build()
                                                         .readerFor(ConnectedInfo.class);
    }

    private Future<KinoticConnection> start() {
        // a promise of the context reports a throw from the caller's handler to the context, where a context-less
        // one would drop it
        Promise<KinoticConnection> promise = context.promise();
        initialConnect = promise;
        context.runOnContext(v -> {
            long connectionTimeoutMillis = options.getReconnect().getConnectionTimeout().toMillis();
            // the TCP connection is bounded here, the WebSocket handshake by the connect options of each attempt
            WebSocketClientOptions webSocketClientOptions = new WebSocketClientOptions()
                    .setMaxFrameSize(options.getMaxWebSocketFrameSize())
                    .setConnectTimeout((int) Math.min(Integer.MAX_VALUE, connectionTimeoutMillis));
            stompClient = StompClient.create(vertx, new StompClientOptions()
                    .setWebSocketClientOptions(webSocketClientOptions)
                    .setMaxBodyLength(options.getMaxBodyLength())
                    .setConnectTimeout(connectionTimeoutMillis));
            attemptConnection();
        });
        return promise.future();
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface) {
        Validate.notNull(serviceInterface, "serviceInterface must not be null");
        requireOpen();
        return track(proxyFactory.serviceProxy(serviceInterface));
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface) {
        Validate.notNull(serviceIdentifier, "serviceIdentifier must not be null");
        Validate.notNull(serviceInterface, "serviceInterface must not be null");
        requireOpen();
        return track(proxyFactory.serviceProxy(serviceIdentifier, serviceInterface));
    }

    @Override
    public ConnectedInfo connectedInfo() {
        return connectedInfo;
    }

    @Override
    public ConnectionState state() {
        return state;
    }

    @Override
    public KinoticConnection stateHandler(Handler<ConnectionState> handler) {
        this.stateHandler = handler;
        return this;
    }

    @Override
    public Future<Void> close() {
        Promise<Void> promise = context.promise();
        context.runOnContext(v -> closeOnContext().onComplete(promise));
        return promise.future();
    }

    private void requireOpen() {
        Validate.validState(!closing, "The connection to %s is closed", options.getServerUrl());
    }

    // Held until released, so close() ends every proxy the connection minted
    private <T> RpcServiceProxyHandle<T> track(RpcServiceProxyHandle<T> handle) {
        RpcServiceProxyHandle<T> ret = new RpcServiceProxyHandle<>() {
            @Override
            public T getService() {
                return handle.getService();
            }

            @Override
            public void release() {
                handles.remove(this);
                handle.release();
            }
        };
        handles.add(ret);
        // a close() that ran between requireOpen() and this add has released the handles it found
        if (closing) {
            ret.release();
        }
        requireOpen();
        return ret;
    }

    private void attemptConnection() {
        reconnectTimer = -1;
        try {
            ReconnectOptions reconnect = options.getReconnect();
            WebSocketConnectOptions webSocketOptions = new WebSocketConnectOptions()
                    .setAbsoluteURI(options.getServerUrl().replaceAll("/+$", "") + GATEWAY_PATH)
                    .setTimeout(reconnect.getConnectionTimeout().toMillis());
            if (options.getSslOptions() != null) {
                webSocketOptions.setSslOptions(options.getSslOptions());
            }
            options.getAuthHeaders().forEach(webSocketOptions::putHeader);

            // the TCP connection is bounded by the WebSocket client's options, the handshake by these connect options
            // and the gateway's answer to CONNECT by the STOMP client, each with the connection timeout
            stompClient.connect(webSocketOptions, CONNECT_HEADERS)
                       .onComplete(ar -> {
                           if (ar.succeeded()) {
                               connectionAccepted(ar.result());
                           } else {
                               attemptFailed(ar.cause());
                           }
                       });
        } catch (Exception e) {
            // a throw here would otherwise leave the attempt unsettled, which no caller can detect
            attemptFailed(e);
        }
    }

    private void connectionAccepted(StompClientConnection connection) {
        if (closing) {
            connection.close();
        } else {
            ConnectedInfo info;
            try {
                info = readConnectedInfo(connection);
            } catch (Exception e) {
                info = null;
                connection.close();
                // a CONNECTED frame without readable info is a protocol violation: it ends the initial connect
                // like a refused CONNECT, and after an established connection it is a failed attempt like a 401
                if (connections == 0) {
                    endForGood(e);
                } else {
                    attemptFailed(e);
                }
            }
            if (info != null) {
                connections++;
                failedAttempts = 0;
                String connectionId = options.getServerUrl() + "#" + connections;
                current = connection;
                currentConnectionId = connectionId;
                connectedInfo = info;
                connection.closeHandler(cause -> connectionLost(connectionId, cause));
                livenessWatcher.connected(connectionId);
                transport.connected(connection, connectionId, info.getReplyToId());
                log.info("Connected to {} as participant {}", options.getServerUrl(), info.getParticipant().getId());
                changeState(ConnectionState.CONNECTED);
                Promise<KinoticConnection> pending = initialConnect;
                initialConnect = null;
                if (pending != null) {
                    pending.complete(this);
                }
            }
        }
    }

    private ConnectedInfo readConnectedInfo(StompClientConnection connection) {
        String json = connection.connectedFrame().getHeader(EventConstants.CONNECTED_INFO_HEADER);
        Validate.validState(json != null, "The gateway at %s accepted the connection without a %s header",
                            options.getServerUrl(), EventConstants.CONNECTED_INFO_HEADER);
        ConnectedInfo ret = connectedInfoReader.readValue(json);
        Validate.validState(ret.getReplyToId() != null, "The gateway at %s accepted the connection without a reply-to id",
                            options.getServerUrl());
        Validate.validState(ret.getParticipant() != null, "The gateway at %s accepted the connection without a participant",
                            options.getServerUrl());
        return ret;
    }

    private void connectionLost(String connectionId, Throwable cause) {
        // a connection closed by an attempt that was already settled was never the current one
        if (connectionId.equals(currentConnectionId)) {
            current = null;
            currentConnectionId = null;
            transport.disconnected();
            Future<Void> pendingFailed = livenessWatcher.connectionEnded(connectionId);
            if (closing) {
                if (cause != null) {
                    log.warn("The connection to {} did not close cleanly", options.getServerUrl(), cause);
                }
            } else {
                log.warn("The connection to {} was lost, reconnecting", options.getServerUrl(), cause);
                // reported once every pending call has failed, on whichever context its caller's watch ran that,
                // unless a connection was established or the close began in the meantime
                pendingFailed.onComplete(ar -> context.runOnContext(v -> {
                    if (!closing && current == null) {
                        changeState(ConnectionState.RECONNECTING);
                    }
                }));
                scheduleAttempt();
            }
        }
    }

    private void attemptFailed(Throwable cause) {
        if (!closing) {
            Throwable failure = mapConnectFailure(cause);
            failedAttempts++;
            int maxAttempts = options.getReconnect().getMaxAttempts();
            if (isFatal(failure) || (maxAttempts > 0 && failedAttempts >= maxAttempts)) {
                endForGood(failure);
            } else {
                long delay = nextDelay();
                log.warn("Connection attempt {} to {} failed, retrying in {} ms: {}", failedAttempts, options.getServerUrl(), delay, failure.toString());
                scheduleAttempt(delay);
            }
        }
    }

    // The gateway authenticates the WebSocket upgrade and answers rejected credentials with a 401
    private Throwable mapConnectFailure(Throwable cause) {
        Throwable ret = cause;
        if (cause instanceof UpgradeRejectedException rejected && rejected.getStatus() == HTTP_UNAUTHORIZED) {
            ret = new AuthenticationException("The gateway at " + options.getServerUrl() + " rejected the credentials", cause);
        }
        return ret;
    }

    // Rejected credentials and a refused CONNECT frame end the initial connect, since neither changes on a retry.
    // After an established connection the same answers can be a gateway recovering, whose security service maps a
    // failure of its own to a 401, so a reconnect keeps retrying within the attempt budget
    private boolean isFatal(Throwable failure) {
        return connections == 0 && (failure instanceof AuthenticationException || failure instanceof StompErrorException);
    }

    private void scheduleAttempt() {
        scheduleAttempt(nextDelay());
    }

    private void scheduleAttempt(long delay) {
        reconnectTimer = vertx.setTimer(delay, id -> attemptConnection());
    }

    // The delay doubles with every consecutive failure up to the maximum; after an established connection, jitter
    // spreads the clients a gateway restart drops
    private long nextDelay() {
        ReconnectOptions reconnect = options.getReconnect();
        long max = reconnect.getMaxDelay().toMillis();
        long delay = reconnect.getInitialDelay().toMillis();
        for (int i = 1; i < failedAttempts && delay < max; i++) {
            delay *= 2;
        }
        delay = Math.min(delay, max);
        long maxJitter = reconnect.getMaxJitter().toMillis();
        if (connections > 0 && maxJitter > 0) {
            delay += ThreadLocalRandom.current().nextLong(maxJitter);
        }
        return delay;
    }

    // Connecting is over for good: the initial connect fails with the cause, an established connection closes
    private void endForGood(Throwable cause) {
        log.error("The connection to {} ended", options.getServerUrl(), cause);
        Promise<KinoticConnection> pending = initialConnect;
        initialConnect = null;
        closeOnContext();
        if (pending != null) {
            pending.fail(cause);
        }
    }

    private Future<Void> closeOnContext() {
        if (closed == null) {
            closing = true;
            if (reconnectTimer != -1) {
                vertx.cancelTimer(reconnectTimer);
                reconnectTimer = -1;
            }
            handles.forEach(RpcServiceProxyHandle::release);
            handles.clear();
            Future<Void> disconnected = current != null ? current.disconnect() : Future.succeededFuture();
            closed = disconnected.transform(ar -> stompClient != null ? stompClient.close() : Future.succeededFuture())
                                 .transform(ar -> {
                                     changeState(ConnectionState.CLOSED);
                                     return Future.<Void>succeededFuture();
                                 });
        }
        return closed;
    }

    private void changeState(ConnectionState newState) {
        if (state != newState) {
            state = newState;
            Handler<ConnectionState> handler = stateHandler;
            if (handler != null) {
                try {
                    handler.handle(newState);
                } catch (Exception e) {
                    log.error("The state handler failed on {}", newState, e);
                }
            }
        }
    }

}

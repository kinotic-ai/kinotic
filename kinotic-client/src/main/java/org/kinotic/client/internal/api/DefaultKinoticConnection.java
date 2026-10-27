package org.kinotic.client.internal.api;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.UpgradeRejectedException;
import io.vertx.core.http.WebSocketConnectOptions;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default {@link KinoticConnection}: a STOMP connection to the gateway's WebSocket endpoint, kept alive across
 * losses with the retry policy of its {@link ReconnectOptions}, and the service proxies minted over it. The connection
 * owns a Vert.x instance of its own, and everything it does runs on one context of that instance.
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
    private final Context context;
    private final StompRpcTransport transport;
    private final ConnectionLivenessWatcher livenessWatcher;
    private final RpcServiceProxyFactory proxyFactory;
    private final ObjectReader connectedInfoReader;
    private final Set<RpcServiceProxyHandle<?>> handles = ConcurrentHashMap.newKeySet();
    private volatile ConnectionState state;
    private volatile Handler<ConnectionState> stateHandler;
    private volatile ConnectedInfo connectedInfo;
    // touched only on the context
    private StompClient stompClient;
    private StompClientConnection current;
    private String currentConnectionId;
    private int connections = 0;
    private int failedAttempts = 0;
    private long reconnectTimer = -1;
    private Promise<KinoticConnection> initialConnect;
    // written on the context, read by serviceProxy() and close() from any thread
    private volatile boolean closing = false;
    private volatile Future<Void> closed;

    /**
     * Connects to the gateway the options name.
     * @param options where to connect and how to authenticate
     * @return a future completed with the connection once the gateway accepted it
     */
    public static Future<KinoticConnection> connect(ConnectOptions options) {
        Validate.notNull(options, "options must not be null");
        Validate.notBlank(options.getServerUrl(), "serverUrl must not be blank");
        Validate.notNull(options.getAuthHeaders(), "authHeaders must not be null");
        Validate.notNull(options.getReconnect(), "reconnect must not be null");
        URI serverUri = URI.create(options.getServerUrl());
        Validate.isTrue(serverUri.getScheme() != null && WEBSOCKET_SCHEMES.contains(serverUri.getScheme()),
                        "serverUrl must be a ws:// or wss:// URL but was '%s'", options.getServerUrl());
        Validate.isTrue(serverUri.getHost() != null, "serverUrl must name a host but was '%s'", options.getServerUrl());
        Validate.isTrue(options.getAuthHeaders().values().stream().noneMatch(Objects::isNull), "authHeaders must not hold a null value");
        Validate.isTrue(options.getMaxFrameSize() >= 1, "maxFrameSize must be at least 1 byte");
        ReconnectOptions reconnect = options.getReconnect();
        Validate.isTrue(reconnect.getMaxAttempts() >= 0, "reconnect.maxAttempts must not be negative");
        // Vert.x schedules no timer under a millisecond, so the delays and the timeout are at least that
        validateAtLeastOneMillisecond(reconnect.getInitialDelay(), "reconnect.initialDelay");
        validateAtLeastOneMillisecond(reconnect.getMaxDelay(), "reconnect.maxDelay");
        validateAtLeastOneMillisecond(reconnect.getConnectionTimeout(), "reconnect.connectionTimeout");
        Validate.isTrue(reconnect.getMaxJitter() != null && !reconnect.getMaxJitter().isNegative(),
                        "reconnect.maxJitter must not be negative");
        return new DefaultKinoticConnection(options).start();
    }

    private static void validateAtLeastOneMillisecond(Duration duration, String name) {
        Validate.isTrue(duration != null && duration.toMillis() >= 1, "%s must be at least 1 ms", name);
    }

    private DefaultKinoticConnection(ConnectOptions options) {
        this.options = options;
        // the caller's customizer runs before the Vert.x instance exists, so a customizer that throws leaks nothing
        JsonMapper.Builder jsonMapperBuilder = RpcServiceProxyFactory.jsonMapperBuilder();
        if (options.getJsonMapperCustomizer() != null) {
            options.getJsonMapperCustomizer().accept(jsonMapperBuilder);
        }
        JsonMapper jsonMapper = jsonMapperBuilder.build();
        // one event loop carries the connection, and nothing here runs blocking work
        this.vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1)
                                                   .setWorkerPoolSize(1)
                                                   .setInternalBlockingPoolSize(1));
        this.context = vertx.getOrCreateContext();
        this.livenessWatcher = new ConnectionLivenessWatcher(context);
        this.transport = new StompRpcTransport(context, options.getServerUrl(), NODE_NAME);
        KinoticProperties properties = new KinoticProperties().setMaxEventPayloadSize(options.getMaxFrameSize());
        this.proxyFactory = new RpcServiceProxyFactory(transport, livenessWatcher, vertx, properties, jsonMapper, NODE_NAME);
        // the gateway's participant model is not on this classpath, so the participant is read as what every participant has
        SimpleModule participantModule = new SimpleModule("KinoticClientModule")
                .addAbstractTypeMapping(Participant.class, ConnectedParticipant.class);
        this.connectedInfoReader = jsonMapper.rebuild()
                                             .addModule(participantModule)
                                             .build()
                                             .readerFor(ConnectedInfo.class);
    }

    private Future<KinoticConnection> start() {
        Promise<KinoticConnection> promise = Promise.promise();
        initialConnect = promise;
        context.runOnContext(v -> {
            stompClient = StompClient.create(vertx, new StompClientOptions()
                    .setMaxFrameSize(options.getMaxFrameSize())
                    .setConnectTimeout(options.getReconnect().getConnectionTimeout().toMillis()));
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
        Future<Void> ret = closed;
        // the Vert.x instance closes with the connection, so a close that already ran is answered off its context
        if (ret == null) {
            Promise<Void> promise = Promise.promise();
            context.runOnContext(v -> closeOnContext().onComplete(promise));
            ret = promise.future();
        }
        return ret;
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
        ReconnectOptions reconnect = options.getReconnect();
        WebSocketConnectOptions webSocketOptions = new WebSocketConnectOptions()
                .setAbsoluteURI(options.getServerUrl().replaceAll("/+$", "") + GATEWAY_PATH)
                .setTimeout(reconnect.getConnectionTimeout().toMillis());
        if (options.getSslOptions() != null) {
            webSocketOptions.setSslOptions(options.getSslOptions());
        }
        options.getAuthHeaders().forEach(webSocketOptions::putHeader);

        // One outcome per attempt: the first of the CONNECTED frame, a failure and the timeout settles it, and a
        // connection the gateway accepts after that belongs to no attempt and is closed
        AtomicBoolean settled = new AtomicBoolean(false);
        long timer = vertx.setTimer(reconnect.getConnectionTimeout().toMillis(), id -> {
            if (settled.compareAndSet(false, true)) {
                attemptFailed(new TimeoutException("The gateway at " + options.getServerUrl() + " did not accept the connection within "
                                                           + reconnect.getConnectionTimeout().toMillis() + " ms"));
            }
        });
        stompClient.connect(webSocketOptions, CONNECT_HEADERS)
                   .onComplete(ar -> {
                       if (settled.compareAndSet(false, true)) {
                           vertx.cancelTimer(timer);
                           if (ar.succeeded()) {
                               connectionAccepted(ar.result());
                           } else {
                               attemptFailed(ar.cause());
                           }
                       } else if (ar.succeeded()) {
                           ar.result().close();
                       }
                   });
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
            livenessWatcher.connectionEnded(connectionId);
            if (!closing) {
                log.warn("The connection to {} was lost, reconnecting", options.getServerUrl(), cause);
                // the watcher dispatched the failures of the pending calls to this context, so a state change queued
                // behind them is seen once every pending call has failed
                context.runOnContext(v -> {
                    if (!closing) {
                        changeState(ConnectionState.RECONNECTING);
                    }
                });
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
            // a gateway that is gone answers the DISCONNECT with nothing, so its receipt is waited for no longer
            // than an attempt would be, and the socket is closed with the client either way
            Future<Void> disconnected = current != null
                    ? current.disconnect().timeout(options.getReconnect().getConnectionTimeout().toMillis(), TimeUnit.MILLISECONDS)
                    : Future.succeededFuture();
            closed = disconnected.transform(ar -> stompClient != null ? stompClient.close() : Future.succeededFuture())
                                 .transform(ar -> {
                                     changeState(ConnectionState.CLOSED);
                                     return vertx.close();
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

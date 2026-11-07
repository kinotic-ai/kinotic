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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.client.api.ConnectOptions;
import org.kinotic.client.api.ConnectionState;
import org.kinotic.client.api.KinoticConnection;
import org.kinotic.client.api.ReconnectOptions;
import org.kinotic.core.api.RpcServiceProxies;
import org.kinotic.core.api.RpcServiceProxyFactory;
import org.kinotic.core.api.RpcServiceProxyHandle;
import org.kinotic.core.api.event.EventConstants;
import org.kinotic.core.api.event.SessionKeepAliveMode;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.service.ServiceIdentifier;
import tools.jackson.databind.ObjectReader;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Default {@link KinoticConnection}: a STOMP connection to the gateway's WebSocket endpoint, kept alive across
 * losses with the retry policy of its {@link ReconnectOptions}, and the service proxies minted over it. Everything it
 * does runs on one context of the application's Vert.x instance.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Slf4j
public class DefaultKinoticConnection implements KinoticConnection {

    /**
     * Names this process in the reply destinations of its proxies.
     */
    private static final String NODE_NAME = "kinotic-client";

    private static final int HTTP_UNAUTHORIZED = 401;

    // a client outside a browser presents no session cookie, so the gateway keeps nothing for it between connections
    private static final Map<String, String> CONNECT_HEADERS = Map.of(EventConstants.SESSION_KEEP_ALIVE_HEADER,
                                                                      SessionKeepAliveMode.NONE.name());

    private final ConnectOptions options;
    private final String endpointUrl;
    private final Vertx vertx;
    private final ContextInternal context;
    private final StompRpcTransport transport;
    private final ConnectionLivenessWatcher livenessWatcher;
    private final RpcServiceProxies proxies;
    private final ObjectReader connectedInfoReader;
    private final Promise<KinoticConnection> initialConnect;
    private final Set<RpcServiceProxyHandle<?>> handles = ConcurrentHashMap.newKeySet();
    private volatile ConnectionState state;
    private volatile Handler<ConnectionState> stateHandler;
    private volatile ConnectedInfo connectedInfo;
    // set on the context once the close began, read by serviceProxy() from any thread
    private volatile Future<Void> closed;
    // touched only on the context
    private StompClient stompClient;
    private StompClientConnection current;
    private int connections = 0;
    private int failedAttempts = 0;
    private long reconnectTimer = -1;

    DefaultKinoticConnection(Vertx vertx, ConnectOptions options, RpcServiceProxyFactory proxyFactory, ObjectReader connectedInfoReader) {
        this.options = options;
        this.endpointUrl = StringUtils.stripEnd(options.getServerUrl(), "/") + EventConstants.STOMP_WEBSOCKET_PATH;
        this.vertx = vertx;
        // a duplicate starts with empty locals, so the connection carries nothing of the context connect() was
        // called on and shares its context with no other connection
        this.context = ((ContextInternal) vertx.getOrCreateContext()).duplicate();
        // a promise of the context reports a throw from the caller's handler to the context, where a context-less
        // one would drop it
        this.initialConnect = context.promise();
        this.livenessWatcher = new ConnectionLivenessWatcher(vertx);
        this.transport = new StompRpcTransport(context, options.getServerUrl(), NODE_NAME,
                                               options.getReconnect().getConnectionTimeout().toMillis());
        this.proxies = proxyFactory.over(transport, livenessWatcher, NODE_NAME);
        this.connectedInfoReader = connectedInfoReader;
    }

    Future<KinoticConnection> start() {
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
        return initialConnect.future();
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface) {
        Validate.notNull(serviceInterface, "serviceInterface must not be null");
        return track(proxies.serviceProxy(serviceInterface));
    }

    @Override
    public <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface) {
        Validate.notNull(serviceIdentifier, "serviceIdentifier must not be null");
        Validate.notNull(serviceInterface, "serviceInterface must not be null");
        return track(proxies.serviceProxy(serviceIdentifier, serviceInterface));
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
        Validate.validState(closed == null, "The connection to %s is closed", options.getServerUrl());
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
        // a close() that began before this add has released the handles it found, so this one releases itself
        if (closed != null) {
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
                    .setAbsoluteURI(endpointUrl)
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
        if (closed != null) {
            connection.close();
            return;
        }
        ConnectedInfo info;
        try {
            info = readConnectedInfo(connection);
        } catch (Exception e) {
            connection.close();
            // a CONNECTED frame without readable info is a protocol violation: it ends the initial connect like a
            // refused CONNECT, and after an established connection it is a failed attempt like a 401
            if (connections == 0) {
                endForGood(e);
            } else {
                attemptFailed(e);
            }
            return;
        }
        connections++;
        failedAttempts = 0;
        String connectionId = options.getServerUrl() + "#" + connections;
        current = connection;
        connectedInfo = info;
        connection.closeHandler(cause -> connectionLost(connection, connectionId, cause));
        livenessWatcher.connected(connectionId);
        transport.connected(connection, connectionId, info.getReplyToId());
        log.info("Connected to {} as participant {}", options.getServerUrl(), info.getParticipant().getId());
        changeState(ConnectionState.CONNECTED);
        initialConnect.tryComplete(this);
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

    private void connectionLost(StompClientConnection connection, String connectionId, Throwable cause) {
        // a connection closed by an attempt that was already settled was never the current one
        if (connection == current) {
            current = null;
            transport.disconnected();
            Future<Void> pendingFailed = livenessWatcher.connectionEnded(connectionId);
            if (closed != null) {
                if (cause != null) {
                    log.warn("The connection to {} did not close cleanly", options.getServerUrl(), cause);
                }
            } else {
                log.warn("The connection to {} was lost, reconnecting", options.getServerUrl(), cause);
                // reported once every pending call has failed, on whichever context its caller's watch ran that,
                // unless a connection was established or the close began in the meantime
                pendingFailed.onComplete(ar -> context.runOnContext(v -> {
                    if (closed == null && current == null) {
                        changeState(ConnectionState.RECONNECTING);
                    }
                }));
                scheduleAttempt(nextDelay());
            }
        }
    }

    private void attemptFailed(Throwable cause) {
        if (closed == null) {
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

    // A 4xx answer to the upgrade is the gateway's verdict on this client as configured, which no retry changes, so
    // it ends the connect, or the connection, for good; a failure to reach the gateway or a 5xx answer is retried
    // within the attempt budget. Rejected credentials and a refused CONNECT frame end the initial connect only:
    // the STOMP server answers every failed handshake with a 401, so after an established connection they can be
    // a gateway whose security service is failing, and a reconnect keeps retrying them within the budget
    private boolean isFatal(Throwable failure) {
        boolean ret;
        if (failure instanceof AuthenticationException || failure instanceof StompErrorException) {
            ret = connections == 0;
        } else {
            ret = failure instanceof UpgradeRejectedException rejected
                    && rejected.getStatus() >= 400 && rejected.getStatus() < 500;
        }
        return ret;
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
        closeOnContext();
        initialConnect.tryFail(cause);
    }

    private Future<Void> closeOnContext() {
        if (closed == null) {
            Promise<Void> done = context.promise();
            // set before the handles are released, so a proxy minted meanwhile releases itself
            closed = done.future();
            if (reconnectTimer != -1) {
                vertx.cancelTimer(reconnectTimer);
                reconnectTimer = -1;
            }
            handles.forEach(RpcServiceProxyHandle::release);
            handles.clear();
            Future<Void> disconnected = current != null ? current.disconnect() : Future.succeededFuture();
            disconnected.transform(ar -> stompClient.close())
                        .transform(ar -> {
                            changeState(ConnectionState.CLOSED);
                            return Future.<Void>succeededFuture();
                        })
                        .onComplete(done);
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

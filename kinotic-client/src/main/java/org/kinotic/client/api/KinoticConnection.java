package org.kinotic.client.api;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.kinotic.core.api.RpcServiceProxyHandle;
import org.kinotic.core.api.security.ConnectedInfo;
import org.kinotic.core.api.service.ServiceIdentifier;

/**
 * A connection to a Kinotic gateway, through which a Java program outside the gateway's cluster calls the
 * services published there with the same typed proxies a cluster member uses. The connection authenticates with
 * the headers of its {@link ConnectOptions}, keeps itself alive with STOMP heartbeats, reconnects when it is lost
 * and reports its {@link ConnectionState}; which failures are retried, and how often, is the policy of its
 * {@link ReconnectOptions}. A call pending when the connection is lost fails with
 * an {@link org.kinotic.core.api.exceptions.RpcServiceUnavailableException}, since nothing the gateway held for the
 * connection survives it. Every handler the connection invokes runs on the connection's context of the
 * application's Vert.x instance, and a proxy's result completes there too, since replies arrive on it; a request that
 * fails before the gateway acknowledged it completes its result on the context the call was made on. Nothing
 * run on the connection's context may block it.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
public interface KinoticConnection {

    /**
     * Creates a proxy for the service the interface addresses: a {@link org.kinotic.core.api.annotations.Publish}ed
     * interface, or a {@link org.kinotic.core.api.annotations.Proxy} declaring the service's namespace and name.
     * Every method of the interface must return a {@code CompletableFuture}, a Vert.x {@code Future}, a {@code Mono}
     * or a {@code Flux}.
     *
     * @param serviceInterface the interface of the service
     * @param <T> the type of the serviceInterface
     * @return the handle giving access to the proxy; releasing it ends the proxy, and {@link #close()} releases every
     *         proxy still held
     */
    <T> RpcServiceProxyHandle<T> serviceProxy(Class<T> serviceInterface);

    /**
     * Creates a proxy for the service identified, implementing the interface.
     *
     * @param serviceIdentifier the identifier of the service to be accessed
     * @param serviceInterface  the interface that the service implements and the target class of the returned proxy
     * @param <T> the type of the serviceInterface
     * @return the handle giving access to the proxy; releasing it ends the proxy, and {@link #close()} releases every
     *         proxy still held
     */
    <T> RpcServiceProxyHandle<T> serviceProxy(ServiceIdentifier serviceIdentifier, Class<T> serviceInterface);

    /**
     * @return what the gateway reported for the current connection: the authenticated participant and the reply
     *         destination id, issued anew by every reconnect
     */
    ConnectedInfo connectedInfo();

    /**
     * @return the current state of the connection
     */
    ConnectionState state();

    /**
     * Sets the handler invoked on every change of {@link #state()}, with the new state.
     *
     * @param handler the handler
     * @return this for fluent usage
     */
    KinoticConnection stateHandler(Handler<ConnectionState> handler);

    /**
     * Releases every proxy, disconnects from the gateway and frees the connection's resources.
     *
     * @return a future completed when the connection is closed
     */
    Future<Void> close();

}

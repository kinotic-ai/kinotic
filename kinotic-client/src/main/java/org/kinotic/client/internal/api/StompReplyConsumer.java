package org.kinotic.client.internal.api;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.event.CRI;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConsumer;

/**
 * A proxy's reply consumer over the gateway: it is subscribed on every connection under a reply destination the
 * gateway authorizes for that connection, and delivers the replies to the proxy's handler. The address the proxy
 * listens on stays the same across reconnects; the destination subscribed on the gateway changes with each.
 *
 * Created by Navíd Mitchell 🤪 on 10/25/26.
 */
@Slf4j
public class StompReplyConsumer implements EventConsumer {

    private final CRI address;
    private final StompRpcTransport transport;
    private final Promise<Void> completion = Promise.promise();
    private volatile Handler<Event<byte[]>> handler;
    private volatile Handler<Throwable> exceptionHandler;
    private volatile Handler<Void> endHandler;
    private volatile boolean registered = true;
    // the subscription on the current connection; touched only on the transport's context
    private String subscriptionId;
    private CRI destination;

    StompReplyConsumer(CRI address, StompRpcTransport transport) {
        this.address = address;
        this.transport = transport;
    }

    /**
     * @return the address the proxy listens on, which its requests name as their reply-to
     */
    CRI listenAddress() {
        return address;
    }

    /**
     * @return the reply destination subscribed on the current connection, or null while there is no connection
     */
    CRI destination() {
        return destination;
    }

    String subscriptionId() {
        return subscriptionId;
    }

    boolean hasHandler() {
        return handler != null;
    }

    void subscribed(String subscriptionId, CRI destination) {
        this.subscriptionId = subscriptionId;
        this.destination = destination;
        completion.tryComplete();
    }

    void unsubscribed() {
        this.subscriptionId = null;
        this.destination = null;
    }

    void deliver(Event<byte[]> event) {
        Handler<Event<byte[]>> current = handler;
        if (current != null) {
            try {
                current.handle(event);
            } catch (Exception e) {
                Handler<Throwable> onException = exceptionHandler;
                if (onException != null) {
                    onException.handle(e);
                } else {
                    log.error("Unhandled exception delivering a reply to {}", address, e);
                }
            }
        }
    }

    @Override
    public EventConsumer handler(Handler<Event<byte[]>> handler) {
        this.handler = handler;
        transport.consumerReady(this);
        return this;
    }

    @Override
    public EventConsumer exceptionHandler(Handler<Throwable> handler) {
        this.exceptionHandler = handler;
        return this;
    }

    @Override
    public EventConsumer endHandler(Handler<Void> handler) {
        this.endHandler = handler;
        return this;
    }

    @Override
    public EventConsumer pause() {
        throw new UnsupportedOperationException("A reply consumer over the gateway cannot be paused");
    }

    @Override
    public EventConsumer resume() {
        throw new UnsupportedOperationException("A reply consumer over the gateway cannot be paused");
    }

    @Override
    public Future<Void> unregister() {
        registered = false;
        Future<Void> ret = transport.remove(this);
        Handler<Void> onEnd = endHandler;
        if (onEnd != null) {
            onEnd.handle(null);
        }
        return ret;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    @Override
    public String address() {
        return address.baseResource();
    }

    @Override
    public Future<Void> completion() {
        return completion.future();
    }

}

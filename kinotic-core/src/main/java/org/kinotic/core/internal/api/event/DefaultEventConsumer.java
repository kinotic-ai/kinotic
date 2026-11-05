


package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.MessageConsumer;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConsumer;


/**
 * Default implementation of {@link EventConsumer} that wraps a Vert.x {@link MessageConsumer}.
 * The message body is already an {@link Event} (produced by the registered event codec), so it is
 * handed straight to the handler. A sender that asked for an acknowledgement receives the id of the
 * node this consumer runs on, so it knows which node took the event.
 *
 * Created by Navid Mitchell on 2024-01-01.
 */
public class DefaultEventConsumer implements EventConsumer {

    private final MessageConsumer<Event<byte[]>> delegate;
    private final String nodeId;
    private final Runnable onUnregister;
    // the one unregistration of this consumer, which every call to unregister() returns
    private Future<Void> unregistration;

    /**
     * @param delegate the Vert.x consumer to wrap
     * @param nodeId the id of the node this consumer runs on, sent as the acknowledgement
     * @param onUnregister run once when this consumer is first unregistered; may be null
     */
    public DefaultEventConsumer(MessageConsumer<Event<byte[]>> delegate, String nodeId, Runnable onUnregister) {
        this.delegate = delegate;
        this.nodeId = nodeId;
        this.onUnregister = onUnregister;
    }

    @Override
    public EventConsumer handler(Handler<Event<byte[]>> handler) {
        this.delegate.handler(message -> {
            // ack receipt if the sender asked for it; naming this node lets the sender tie the
            // request to the one registration that took it when the address has several
            if (message.replyAddress() != null) {
                message.reply(nodeId);
            }
            handler.handle(message.body());
        });
        return this;
    }

    @Override
    public EventConsumer exceptionHandler(Handler<Throwable> handler) {
        delegate.exceptionHandler(handler);
        return this;
    }

    @Override
    public EventConsumer endHandler(Handler<Void> handler) {
        delegate.endHandler(handler);
        return this;
    }

    @Override
    public EventConsumer pause() {
        delegate.pause();
        return this;
    }

    @Override
    public EventConsumer resume() {
        delegate.resume();
        return this;
    }

    // Synchronized so that two calls share one unregistration: the delegate replaces its completion promise
    // once unregistered, and a second call reading the fresh promise would wait on it forever
    @Override
    public synchronized Future<Void> unregister() {
        if (unregistration == null) {
            if (onUnregister != null) {
                onUnregister.run();
            }
            // Vert.x fails a registration still in progress when the consumer is unregistered under it, which a
            // caller waiting on completion() would read as a failed registration, so the unregistration waits
            // for a started registration to settle either way
            if (delegate.isRegistered()) {
                unregistration = delegate.completion().transform(ar -> delegate.unregister());
            } else {
                unregistration = delegate.unregister();
            }
        }
        return unregistration;
    }

    @Override
    public String address() {
        return delegate.address();
    }

    @Override
    public boolean isRegistered() {
        return delegate.isRegistered();
    }

    @Override
    public Future<Void> completion() {
        return delegate.completion();
    }
}

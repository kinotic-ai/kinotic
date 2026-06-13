


package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.MessageConsumer;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConsumer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of {@link EventConsumer} that wraps a Vert.x {@link MessageConsumer}.
 * The message body is already an {@link Event} (produced by the registered event codec), so it is
 * handed straight to the handler.
 *
 * Created by Navid Mitchell on 2024-01-01.
 */
public class DefaultEventConsumer implements EventConsumer {

    private final MessageConsumer<Event<byte[]>> delegate;
    private final Runnable onUnregister;
    private final AtomicBoolean unregistered = new AtomicBoolean(false);

    public DefaultEventConsumer(MessageConsumer<Event<byte[]>> delegate) {
        this(delegate, null);
    }

    /**
     * @param delegate the Vert.x consumer to wrap
     * @param onUnregister run once when this consumer is first unregistered; may be null
     */
    public DefaultEventConsumer(MessageConsumer<Event<byte[]>> delegate, Runnable onUnregister) {
        this.delegate = delegate;
        this.onUnregister = onUnregister;
    }

    @Override
    public EventConsumer handler(Handler<Event<byte[]>> handler) {
        this.delegate.handler(message -> {
            // ack that we received the message if desired by sender
            if (message.replyAddress() != null) {
                message.reply(null);
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

    @Override
    public Future<Void> unregister() {
        if (onUnregister != null && unregistered.compareAndSet(false, true)) {
            onUnregister.run();
        }
        return delegate.unregister();
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




package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.MessageConsumer;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConsumer;

/**
 * Default implementation of {@link EventConsumer} that wraps a Vert.x {@link MessageConsumer}
 * and converts incoming messages to {@link Event} objects via {@link MessageEventAdapter}.
 *
 * Created by Navid Mitchell on 2024-01-01.
 */
public class DefaultEventConsumer implements EventConsumer {

    private final MessageConsumer<byte[]> delegate;

    public DefaultEventConsumer(MessageConsumer<byte[]> delegate) {
        this.delegate = delegate;
    }

    @Override
    public EventConsumer handler(Handler<Event<byte[]>> handler) {
        this.delegate.handler(message -> {
            // ack that we received the message if desired by sender
            if (message.replyAddress() != null) {
                message.reply(null);
            }
            handler.handle(new MessageEventAdapter<>(message));
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

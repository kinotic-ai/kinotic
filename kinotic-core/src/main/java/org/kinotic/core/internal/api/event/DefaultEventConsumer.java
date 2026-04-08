


package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.MessageConsumer;
import org.kinotic.core.api.event.Event;
import org.kinotic.core.api.event.EventConsumer;

/**
 * Default implementation of {@link EventConsumer} that wraps a Vert.x {@link MessageConsumer}
 * and converts incoming messages to {@link Event} objects via {@link MessageEventAdapter}.
 * <p>
 * The Vert.x handler is set in the constructor to trigger consumer registration with the event bus.
 * This is required because {@link MessageConsumer#completion()} will not fire until a handler is set.
 * Messages that arrive before the caller sets a handler via {@link #handler(Handler)} are acknowledged
 * but not processed, matching the documented "hot" semantics.
 *
 * Created by Navid Mitchell on 2024-01-01.
 */
public class DefaultEventConsumer implements EventConsumer {

    private final MessageConsumer<byte[]> delegate;
    private volatile Handler<Event<byte[]>> eventHandler;

    public DefaultEventConsumer(MessageConsumer<byte[]> delegate) {
        this.delegate = delegate;
        // Set the Vert.x handler immediately to trigger consumer registration.
        // completion() won't fire until a handler is set (see MessageConsumerImpl).
        delegate.handler(message -> {
            // ack that we received the message if desired by sender
            if (message.replyAddress() != null) {
                message.reply(null);
            }
            Handler<Event<byte[]>> h = eventHandler;
            if (h != null) {
                h.handle(new MessageEventAdapter<>(message));
            }
        });
    }

    @Override
    public EventConsumer handler(Handler<Event<byte[]>> handler) {
        this.eventHandler = handler;
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

}

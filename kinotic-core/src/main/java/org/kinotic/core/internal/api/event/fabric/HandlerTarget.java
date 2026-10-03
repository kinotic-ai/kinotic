package org.kinotic.core.internal.api.event.fabric;

import io.vertx.core.Handler;

/**
 * A handler registered through {@link org.kinotic.core.api.event.EventFabricService#registerConsumer}, bound to
 * the event type it receives.
 *
 * @param eventType the type the handler takes
 * @param handler   the receiver
 */
record HandlerTarget<T>(Class<T> eventType, Handler<T> handler) implements ConsumerTarget {

    @Override
    public void accept(Object element) {
        handler.handle(eventType.cast(element));
    }

}

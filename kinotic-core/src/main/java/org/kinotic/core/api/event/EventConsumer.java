


package org.kinotic.core.api.event;

import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Provides a Vert.x-style consumer for {@link Event} objects.
 * This wraps the underlying messaging mechanism and provides {@link Event<byte[]>} to handlers
 * rather than raw transport-level messages.
 *
 * Created by Navid Mitchell on 2024-01-01.
 */
public interface EventConsumer {

    /**
     * Sets a handler to receive {@link Event} objects as they arrive.
     * @param handler the handler to process incoming events
     * @return this for fluent usage
     */
    EventConsumer handler(Handler<Event<byte[]>> handler);

    /**
     * Sets an exception handler for errors that occur during event consumption.
     * @param handler the handler to process errors
     * @return this for fluent usage
     */
    EventConsumer exceptionHandler(Handler<Throwable> handler);

    /**
     * Sets a handler that is called when the event stream ends.
     * Under normal operation this should not occur.
     * @param handler the handler called on stream end
     * @return this for fluent usage
     */
    EventConsumer endHandler(Handler<Void> handler);

    /**
     * Pauses the consumer. While paused, no events will be delivered to the handler.
     * @return this for fluent usage
     */
    EventConsumer pause();

    /**
     * Resumes the consumer after a pause.
     * @return this for fluent usage
     */
    EventConsumer resume();

    /**
     * Unregisters the consumer, stopping event delivery and releasing resources.
     * @return a {@link Future} that completes when unregistration is complete
     */
    Future<Void> unregister();

}

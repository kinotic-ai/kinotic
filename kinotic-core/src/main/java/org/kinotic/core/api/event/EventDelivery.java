package org.kinotic.core.api.event;

/**
 * What a {@link org.kinotic.core.api.annotations.KinoticConsumer} method needs from the delivery of its events. The
 * consumer declares it, and {@link EventService#publish(Object)} delivers each event to every consumer as that
 * consumer declared.
 */
public enum EventDelivery {
    /**
     * Every running node with the consumer receives its own copy of the event. A node that cannot be reached while
     * the event is published never receives it, and the publisher is not told.
     */
    BEST_EFFORT,
    /**
     * Every running node with the consumer handles the event before the publish completes. A node that fails or
     * does not answer in time is delivered the event again, so the consumer must be idempotent, and the publish
     * fails once the retries are spent.
     */
    CONFIRMED
}

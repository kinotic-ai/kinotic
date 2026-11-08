package org.kinotic.core.api.event;

/**
 * Handles the {@link ClusterEvent}s of one type that a {@link ClusterEventPublisher} delivers to this node. Every
 * bean implementing this interface for an event's type handles each delivery of that event, and the publisher counts
 * the node as having handled it once each has returned.
 *
 * @param <E> the type of event handled
 */
public interface ClusterEventHandler<E extends ClusterEvent> {

    /**
     * Acts on {@code event} on this node. A delivery a node fails or does not confirm in time is retried, so the same
     * event can arrive more than once and handling it must be idempotent. A thrown exception fails the delivery to
     * this node.
     *
     * @param event the event delivered
     */
    void handle(E event);

}

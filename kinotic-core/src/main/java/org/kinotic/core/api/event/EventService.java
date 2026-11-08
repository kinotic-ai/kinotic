


package org.kinotic.core.api.event;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.KinoticConsumer;

/**
 * Sends and receives the events of the cluster. Raw events are routed by the scheme of their {@link CRI}: every
 * {@code srv://} CRI goes to the {@link EventBusService} and every {@code stream://} CRI to the
 * {@link EventStreamService}. Typed events are published to the {@link KinoticConsumer} methods of their type on every
 * node.
 *
 * Created by navid on 12/19/19
 */
public interface EventService {

    Future<Void> send(Event<byte[]> event);

    /**
     * Creates a new {@link EventConsumer} that will receive {@link Event} with a byte[] from the given destination.
     *
     * @param cri to subscribe to
     * @return the newly created {@link EventConsumer} for the given cri
     */
    EventConsumer listen(String cri);

    /**
     * Publishes {@code event} to every {@link KinoticConsumer} method of its type on every node, this one included.
     * Fire-and-forget with at-most-once delivery: nothing confirms that a consumer received it, and a node that
     * cannot be reached while the event is published never receives it.
     *
     * @param event the event, a concrete class Jackson can write
     */
    void publish(Object event);

    /**
     * Publishes {@code event} to the {@link KinoticConsumer} methods of its type on every node that has one, this one
     * included, and completes once each of those nodes has handled it. A node that fails or does not answer in time
     * is retried against the consumers registered then, so a node that left is not waited for and one that
     * registered a consumer meanwhile receives it; a node that already handled the event handles it again on a
     * retry, so its consumers must be idempotent. Completes at once when no node has a consumer of the type, and
     * fails once the retries are spent, with the last node's failure as the cause.
     *
     * @param event the event, a concrete class Jackson can write
     * @return a future completed once every node consuming the event's type has handled it
     */
    Future<Void> publishConfirmed(Object event);

}

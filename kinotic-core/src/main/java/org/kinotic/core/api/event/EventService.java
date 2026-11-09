


package org.kinotic.core.api.event;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.KinoticConsumer;

/**
 * Sends and receives the events of the cluster. Raw events are routed by the scheme of their {@link CRI}: every
 * {@code srv://} CRI goes to the {@link EventBusService} and every {@code stream://} CRI to the
 * {@link EventStreamService}. Typed events are published to the {@link KinoticConsumer} methods of their type on every
 * node, delivered as each consumer declares.
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
     * Publishes {@code event} to every {@link KinoticConsumer} method of its type on every node, this one included,
     * delivering it to each consumer as the consumer's {@link KinoticConsumer#delivery()} declares. The returned
     * future completes once every node with an {@link EventDelivery#CONFIRMED} consumer of the type has handled the
     * event, at once when no node has one, and fails once the retries to such a node are spent, with the last
     * failure as the cause. {@link EventDelivery#BEST_EFFORT} consumers never affect it. Fails at once when
     * {@code event} is null or cannot be written as JSON. Delivery to {@link EventDelivery#CONFIRMED} consumers is
     * at least once: a retry goes to every node that still has a confirmed consumer of the type, so a consumer can
     * receive the same event more than once and must handle it idempotently.
     *
     * @param event the event, a concrete class Jackson can write
     * @return a future completed once every confirmed consumer of the event's type has handled it
     */
    Future<Void> publish(Object event);

}

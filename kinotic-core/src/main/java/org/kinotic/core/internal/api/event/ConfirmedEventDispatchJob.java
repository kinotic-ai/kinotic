package org.kinotic.core.internal.api.event;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.ignite.compute.ComputeJobContext;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.JobContextResource;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.springframework.context.ApplicationContext;

/**
 * Carries an event published with {@link org.kinotic.core.api.event.EventService#publish(Object)} to a node with a
 * {@link org.kinotic.core.api.event.EventDelivery#CONFIRMED} consumer of its type, as the compute job the publisher
 * broadcasts, and hands it there to the node's confirmed consumers of the type, completing once they have handled it.
 * Each broadcast needs its own instance.
 */
@RequiredArgsConstructor
public class ConfirmedEventDispatchJob implements IgniteRunnable {

    @SpringApplicationContextResource
    private transient ApplicationContext applicationContext;

    @JobContextResource
    private transient ComputeJobContext jobContext;

    // the fully qualified name of the event's type
    private final String eventType;
    // the event as JSON
    private final byte[] payload;

    // the consumers' handling of the event, set by the first run() on the receiving node
    private transient volatile Future<Void> delivery;

    // The first run() hands the event to the consumers and holds the job, releasing the Ignite thread while they work;
    // callcc() runs it again once they finish, and that second run() completes the job with their outcome
    @Override
    public void run() {
        if (delivery == null) {
            if (applicationContext == null) {
                throw new IllegalStateException("The Spring context was not injected; Ignite must be started through IgniteSpring");
            }
            delivery = applicationContext.getBean(ConsumerMethodRegistry.class).deliverConfirmed(eventType, payload);
            jobContext.holdcc();
            delivery.onComplete(ar -> jobContext.callcc());
        } else if (delivery.failed()) {
            throw new IllegalStateException("The consumers of " + eventType + " failed to handle it", delivery.cause());
        }
    }

}

package org.kinotic.core.internal.api.event;

import lombok.RequiredArgsConstructor;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Carries an event published with {@link org.kinotic.core.api.event.EventService#publish(Object)} to a node with a
 * {@link org.kinotic.core.api.event.EventDelivery#CONFIRMED} consumer of its type, as the compute job the publisher
 * broadcasts, and hands it there to the node's confirmed consumers of the type, returning once they have handled it.
 */
@RequiredArgsConstructor
public class ConfirmedEventDispatchJob implements IgniteRunnable {

    @SpringApplicationContextResource
    private transient ApplicationContext applicationContext;

    // the fully qualified name of the event's type
    private final String eventType;
    // the event as JSON
    private final byte[] payload;

    @Override
    public void run() {
        if (applicationContext == null) {
            throw new IllegalStateException("The Spring context was not injected; Ignite must be started through IgniteSpring");
        }
        ConsumerMethodRegistry registry = applicationContext.getBean(ConsumerMethodRegistry.class);
        // the Ignite thread waits, so the job's completion means the node's consumers have handled the event
        try {
            registry.deliverConfirmed(eventType, payload)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(DefaultEventService.NODE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while the consumers of " + eventType + " handled it", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("The consumers of " + eventType + " failed to handle it", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("The consumers of " + eventType + " did not handle it within "
                                                    + DefaultEventService.NODE_TIMEOUT_MS + "ms", e);
        }
    }

}

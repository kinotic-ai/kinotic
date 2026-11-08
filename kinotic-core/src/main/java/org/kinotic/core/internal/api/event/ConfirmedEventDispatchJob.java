package org.kinotic.core.internal.api.event;

import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.resources.SpringApplicationContextResource;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Carries an event published with {@link org.kinotic.core.api.event.EventService#publishConfirmed(Object)} to a node,
 * as the compute job the publisher broadcasts, and hands it there to the node's
 * {@link org.kinotic.core.api.annotations.Consumer} methods of its type, returning once they have handled it.
 */
public class ConfirmedEventDispatchJob implements IgniteRunnable {

    @SpringApplicationContextResource
    private transient ApplicationContext applicationContext;

    private final String eventType;
    private final byte[] payload;

    /**
     * @param eventType the fully qualified name of the event's type
     * @param payload   the event as JSON
     */
    public ConfirmedEventDispatchJob(String eventType, byte[] payload) {
        this.eventType = eventType;
        this.payload = payload;
    }

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




package org.kinotic.core.api.event;

import io.vertx.core.Future;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Provides functionality for non-persistent {@link Event}'s
 *
 * Created by navid on 10/30/19
 */
public interface EventBusService {

    /**
     * Send an {@link Event} to through the event bus.
     * @param event to send
     */
    void send(Event<byte[]> event);

    /**
     * Send an {@link Event} to through the event bus.
     * This is a special form of send that requires the receiver to acknowledge receipt of the message.
     * An exception will be signaled if no acknowledgement is sent.
     * @param event to send
     * @return a {@link Future} that completes when the acknowledgement is received or fails on error
     */
    Future<Void> sendWithAck(Event<byte[]> event);

    /**
     * Creates a new {@link EventConsumer} that will receive {@link Event<byte[]>} sent to the
     * {@link CRI#baseResource()} of the given {@link CRI}.
     * The consumer is not registered with the event bus until {@link EventConsumer#handler} is called.
     * Use {@link EventConsumer#completion()} to wait for registration to complete.
     * @param cri whose base resource will be subscribed to
     * @return the newly created {@link EventConsumer}
     */
    EventConsumer listen(CRI cri);

    /**
     * Checks if any listeners are registered for the {@link CRI#baseResource()} of the given {@link CRI}
     * @param cri to check if any listeners are active for
     * @return a {@link Future} that contains true if listeners are active false if not
     */
    Future<Boolean> isAnybodyListening(CRI cri);

    /**
     * Monitors the status of listeners for the {@link CRI#baseResource()} of the given {@link CRI}
     * @param cri to check for registered listeners
     * @return a {@link Flux} that emits the current status on subscribe and every status transition after that
     */
    Flux<ListenerStatus> monitorListenerStatus(CRI cri);

    /**
     * Monitors listener registration changes across all service ({@code srv://}) addresses. Each change carries the
     * address and its resulting status. The stream carries only changes; take a snapshot with
     * {@link #activeServiceAddresses()} to establish a baseline. The stream errors when registration update
     * continuity is lost — resubscribe and re-snapshot to recover.
     * @return a {@link Flux} of {@link ListenerChange}s for service addresses
     */
    Flux<ListenerChange> monitorListenerChanges();

    /**
     * Snapshots every service ({@code srv://}) address that currently has a registered listener.
     * @return a {@link Future} containing the set of active service addresses
     */
    Future<Set<String>> activeServiceAddresses();

}

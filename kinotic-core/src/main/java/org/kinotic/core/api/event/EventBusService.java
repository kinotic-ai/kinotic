


package org.kinotic.core.api.event;

import io.vertx.core.Future;
import reactor.core.publisher.Flux;

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
     * Creates a new {@link EventConsumer} that will receive {@link Event<byte[]>} from the given cri.
     * The consumer is not registered with the event bus until {@link EventConsumer#handler} is called.
     * Use {@link EventConsumer#completion()} to wait for registration to complete.
     * @param cri to subscribe to
     * @return the newly created {@link EventConsumer} for the given cri
     */
    EventConsumer listen(String cri);

    /**
     * Checks if any listeners have been registered for the given {@link CRI}
     * @param cri to check if any listeners are active for
     * @return a {@link Future} that contains true if listeners are active false if not
     */
    Future<Boolean> isAnybodyListening(String cri);

    /**
     * Monitors the status of listeners for the given cri
     * @param cri to check for registered listeners
     * @return a {@link Flux} that returns a stream of statuses for the given listener cri
     */
    Flux<ListenerStatus> monitorListenerStatus(String cri);

}

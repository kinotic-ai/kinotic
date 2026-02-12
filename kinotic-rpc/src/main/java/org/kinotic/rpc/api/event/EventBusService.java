

package org.kinotic.rpc.api.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     */
    Mono<Void> sendWithAck(Event<byte[]> event);

    /**
     * Returns a "Cold" {@link Flux} that will emit {@link Event<byte[]>} from the given cri when subscribed to
     * @param cri to subscribe to
     * @return the newly created {@link Flux} for the given cri
     */
    Flux<Event<byte[]>> listen(String cri);

    /**
     * Returns a {@link Mono} that when subscribed to will produce a "Hot" flux {@link Flux} that will emit {@link Event}'s from the given cri.
     * Because this returns a "Hot" flux some messages can be lost before you can subscribe to the {@link Flux} provided by the {@link Mono}
     *
     * NOTE: the {@link Mono} will not complete until the listener is published cluster wide.
     *       This is handy if you want to know that a listener will receive a message from a remote node before sending it.
     *       Which ultimately precludes the problem of this being a hot flux
     *
     * @param cri to subscribe to
     * @return the newly created {@link Flux<byte[]>} for the given cri
     */
    Mono<Flux<Event<byte[]>>> listenWithAck(String cri);

    /**
     * Checks if any listeners have been registered for the given {@link CRI}
     * @param cri to check if any listeners are active for
     * @return a {@link Mono} that contains true if listeners are active false if not
     */
    Mono<Boolean> isAnybodyListening(String cri);

    /**
     * Monitors the status of listeners for the given cri
     * @param cri to check for registered listeners
     * @return a {@link Flux} that returns a stream of statuses for the given listener cri
     */
    Flux<ListenerStatus> monitorListenerStatus(String cri);

}

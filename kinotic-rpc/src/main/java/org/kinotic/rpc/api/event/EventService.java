

package org.kinotic.rpc.api.event;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Simple facade that sits in front of the {@link EventBusService} and the {@link EventStreamService}
 * This service will route to the correct backend based upon the CRI.
 * All srv:// CRI's will go to the {@link EventBusService}
 * All stream:// CRI's will go to the {@link EventStreamService}
 *
 *
 * Created by navid on 12/19/19
 */
public interface EventService {

    Mono<Void> send(Event<byte[]> event);

    /**
     * Returns a {@link Mono} that when subscribed to will produce a "Hot" flux {@link Flux} that will emit {@link Event} with a byte[] from the given destination.
     * Because this returns a "Hot" flux some messages can be lost before you can subscribe to the {@link Flux} provided by the {@link Mono}
     * NOTE: the {@link Mono} will not complete until the listener is published cluster wide.
     *
     * @param cri to subscribe to
     * @return the newly created {@link Flux} for the given cri
     */
    Flux<Event<byte[]>> listen(String cri);

}

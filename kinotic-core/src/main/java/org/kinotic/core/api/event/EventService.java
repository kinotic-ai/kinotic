


package org.kinotic.core.api.event;

import io.vertx.core.Future;

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

    Future<Void> send(Event<byte[]> event);

    /**
     * Creates a new {@link EventConsumer} that will receive {@link Event} with a byte[] from the given destination.
     *
     * @param cri to subscribe to
     * @return the newly created {@link EventConsumer} for the given cri
     */
    EventConsumer listen(String cri);

}

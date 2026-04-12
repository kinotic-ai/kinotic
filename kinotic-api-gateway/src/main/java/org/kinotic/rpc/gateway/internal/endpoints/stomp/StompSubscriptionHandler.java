


package org.kinotic.rpc.gateway.internal.endpoints.stomp;

import org.kinotic.core.api.event.Event;

/**
 * Handles events delivered to a subscription endpoint.
 *
 * Created by Navid Mitchell on 2024-01-01.
 */
public interface StompSubscriptionHandler {

    void handleEvent(Event<byte[]> event);

    void handleError(Throwable throwable);

}

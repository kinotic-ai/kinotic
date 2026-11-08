package org.kinotic.core.internal.api.event;

import org.kinotic.core.api.event.ClusterEvent;
import org.kinotic.core.api.event.ClusterEventHandler;
import org.kinotic.core.api.security.ConnectionRevocation;

import java.util.List;

/**
 * Records each {@link ConnectionRevocation} it is handed, as a handler of an event type other than the one sent.
 */
record ConnectionRevocationRecorder(List<ClusterEvent> handled) implements ClusterEventHandler<ConnectionRevocation> {

    @Override
    public void handle(ConnectionRevocation event) {
        handled.add(event);
    }

}

package org.kinotic.core.internal.api.event;

import org.kinotic.core.api.event.ClusterEvent;
import org.kinotic.core.api.event.ClusterEventHandler;

import java.util.List;

/**
 * Records each {@link SampleClusterEvent} it is handed.
 */
record SampleClusterEventHandler(List<ClusterEvent> handled) implements ClusterEventHandler<SampleClusterEvent> {

    @Override
    public void handle(SampleClusterEvent event) {
        handled.add(event);
    }

}

package org.kinotic.core.internal.api.event;

import org.kinotic.core.api.ServerType;
import org.kinotic.core.api.event.ClusterEvent;

import java.time.Instant;
import java.util.Set;

/**
 * An event of the shape a module publishes: a record holding a nested record, a JDK value type and an enum set.
 */
record SampleClusterEvent(SampleClusterEventKey key, Instant at, Set<ServerType> serverTypeFilter) implements ClusterEvent {
}

package org.kinotic.core.internal.api.event;

import org.kinotic.core.api.event.ClusterEvent;

import java.time.Instant;
import java.util.Set;

/**
 * An event of the shape a module publishes: a record holding a nested record, a JDK value type and a set.
 */
record SampleClusterEvent(SampleClusterEventKey key, Instant at, Set<String> nodeAttributeFilter) implements ClusterEvent {
}

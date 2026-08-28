package org.kinotic.grindv2.api.model;

import java.util.List;

/**
 * A task produced dynamically discovered tasks at runtime, carrying the discovered subtree as
 * PENDING {@link TaskRecord}s in discovery order.
 *
 * @param taskPath the position of the task that produced the discovery
 * @param tasks    the discovered tasks, recorded PENDING
 */
public record TasksDiscoveredEvent(String taskPath, List<TaskRecord> tasks) implements JobRunEvent {
}

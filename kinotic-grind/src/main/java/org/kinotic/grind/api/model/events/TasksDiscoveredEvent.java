package org.kinotic.grind.api.model.events;

import org.kinotic.grind.api.model.TaskRecord;

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

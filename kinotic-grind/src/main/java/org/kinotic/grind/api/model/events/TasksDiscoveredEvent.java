package org.kinotic.grind.api.model.events;

import org.kinotic.grind.api.model.TaskRecord;

import java.util.List;

/**
 * Tasks of the run became known, carrying the discovered subtree as PENDING
 * {@link TaskRecord}s in discovery order: the definition's own tasks at the start of the run,
 * and the subtree a task produced when it generates tasks at runtime.
 *
 * @param taskPath the position of the task the discovered tasks belong under
 * @param tasks    the discovered tasks, recorded PENDING
 * @param dynamic  true when the task at {@link #taskPath} produced the discovery at runtime,
 *                 false for the definition's own tasks at the start of the run
 */
public record TasksDiscoveredEvent(String taskPath, List<TaskRecord> tasks, boolean dynamic) implements JobRunEvent {
}

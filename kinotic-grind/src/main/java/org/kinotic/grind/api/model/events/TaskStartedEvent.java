package org.kinotic.grind.api.model.events;

/**
 * A task began executing.
 *
 * @param taskPath    the task's position in the run's task tree
 * @param description the task's description
 */
public record TaskStartedEvent(String taskPath, String description) implements JobRunEvent {
}
